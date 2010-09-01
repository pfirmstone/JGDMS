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

package net.jini.loader;

import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.rmi.server.RMIClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.WeakHashMap;
import net.jini.security.Security;

/**
 * Provides static methods for loading classes using {@link
 * RMIClassLoader} with optional verification that the codebase URLs
 * used to load classes provide content integrity (see {@link
 * Security#verifyCodebaseIntegrity
 * Security.verifyCodebaseIntegrity}).
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public final class ClassLoading {

    /**
     * per-thread cache (weakly) mapping verifierLoader values to
     * (soft) sets of codebase values that have been verified (to
     * provide content integrity) with the verifierLoader value
     **/
    private static final ThreadLocal perThreadCache = new ThreadLocal() {
	protected Object initialValue() { return new WeakHashMap(); }
    };

    /**
     * Loads a class using {@link
     * RMIClassLoader#loadClass(String,String,ClassLoader)
     * RMIClassLoader.loadClass}, optionally verifying that the
     * codebase URLs provide content integrity.
     *
     * <p>If <code>verifyCodebaseIntegrity</code> is <code>true</code>
     * and <code>codebase</code> is not <code>null</code>, then this
     * method invokes {@link Security#verifyCodebaseIntegrity
     * Security.verifyCodebaseIntegrity} with <code>codebase</code> as
     * the first argument and <code>verifierLoader</code> as the
     * second argument (this invocation may be skipped if a previous
     * invocation of this method or {@link #loadProxyClass
     * loadProxyClass} has already invoked
     * <code>Security.verifyCodebaseIntegrity</code> with the same
     * value of <code>codebase</code> and the same effective value of
     * <code>verifierLoader</code> as arguments without it throwing an
     * exception).  If <code>Security.verifyCodebaseIntegrity</code>
     * throws a <code>SecurityException</code>, then this method
     * proceeds as if <code>codebase</code> were <code>null</code>.
     * If <code>Security.verifyCodebaseIntegrity</code> throws any
     * other exception, then this method throws that exception.
     *
     * <p>This method then invokes {@link
     * RMIClassLoader#loadClass(String,String,ClassLoader)
     * RMIClassLoader.loadClass} with <code>codebase</code> as the
     * first argument (or <code>null</code> if in the previous step
     * <code>Security.verifyCodebaseIntegrity</code> was invoked and
     * it threw a <code>SecurityException</code>), <code>name</code>
     * as the second argument, and <code>defaultLoader</code> as the
     * third argument.  If <code>RMIClassLoader.loadClass</code>
     * throws a <code>ClassNotFoundException</code>, then this method
     * throws a <code>ClassNotFoundException</code>; if
     * <code>RMIClassLoader.loadClass</code> throws any other
     * exception, then this method throws that exception; otherwise,
     * this method returns the <code>Class</code> returned by
     * <code>RMIClassLoader.loadClass</code>.
     *
     * @param codebase the list of URLs (separated by spaces) to load
     * the class from, or <code>null</code>
     *
     * @param name the name of the class to load
     *
     * @param defaultLoader the class loader value (possibly
     * <code>null</code>) to pass as the <code>defaultLoader</code>
     * argument to <code>RMIClassLoader.loadClass</code>
     *
     * @param verifyCodebaseIntegrity if <code>true</code>, verify
     * that the codebase URLs provide content integrity
     *
     * @param verifierLoader the class loader value (possibly
     * <code>null</code>) to pass to
     * <code>Security.verifyCodebaseIntegrity</code>, if
     * <code>verifyCodebaseIntegrity</code> is <code>true</code>
     *
     * @return the <code>Class</code> object representing the loaded
     * class
     *
     * @throws MalformedURLException if
     * <code>Security.verifyCodebaseIntegrity</code> or
     * <code>RMIClassLoader.loadClass</code> throws a
     * <code>MalformedURLException</code>
     *
     * @throws ClassNotFoundException if
     * <code>RMIClassLoader.loadClass</code> throws a
     * <code>ClassNotFoundException</code>
     *
     * @throws NullPointerException if <code>name</code> is
     * <code>null</code>
     **/
    public static Class loadClass(String codebase,
				  String name,
				  ClassLoader defaultLoader,
				  boolean verifyCodebaseIntegrity,
				  ClassLoader verifierLoader)
	throws MalformedURLException, ClassNotFoundException
    {
	SecurityException verifyException = null;
	if (verifyCodebaseIntegrity && codebase != null) {
	    try {
		verifyIntegrity(codebase, verifierLoader);
	    } catch (SecurityException e) {
		verifyException = e;
		codebase = null;
	    }
	}
	try {
	    return RMIClassLoader.loadClass(codebase, name, defaultLoader);
	} catch (ClassNotFoundException e) {
	    if (verifyException != null) {
		// assume that the verify exception is more important
		throw new ClassNotFoundException(e.getMessage(),
						 verifyException);
	    } else {
		throw e;
	    }
	}
    }

    /**
     * Loads a dynamic proxy class using {@link
     * RMIClassLoader#loadProxyClass(String,String[],ClassLoader)
     * RMIClassLoader.loadProxyClass}, optionally verifying that the
     * codebase URLs provide content integrity.
     *
     * <p>If <code>verifyCodebaseIntegrity</code> is <code>true</code>
     * and <code>codebase</code> is not <code>null</code>, then this
     * method invokes {@link Security#verifyCodebaseIntegrity
     * Security.verifyCodebaseIntegrity} with <code>codebase</code> as
     * the first argument and <code>verifierLoader</code> as the
     * second argument (this invocation may be skipped if a previous
     * invocation of this method or {@link #loadClass loadClass} has
     * already invoked <code>Security.verifyCodebaseIntegrity</code>
     * with the same value of <code>codebase</code> and the same
     * effective value of <code>verifierLoader</code> as arguments
     * without it throwing an exception).  If
     * <code>Security.verifyCodebaseIntegrity</code> throws a
     * <code>SecurityException</code>, then this method proceeds as if
     * <code>codebase</code> were <code>null</code>.  If
     * <code>Security.verifyCodebaseIntegrity</code> throws any other
     * exception, then this method throws that exception.
     *
     * <p>This method invokes {@link
     * RMIClassLoader#loadProxyClass(String,String[],ClassLoader)
     * RMIClassLoader.loadProxyClass} with <code>codebase</code> as
     * the first argument (or <code>null</code> if in the previous
     * step <code>Security.verifyCodebaseIntegrity</code> was invoked
     * and it threw a <code>SecurityException</code>),
     * <code>interfaceNames</code> as the second argument, and
     * <code>defaultLoader</code> as the third argument.  If
     * <code>RMIClassLoader.loadProxyClass</code> throws a
     * <code>ClassNotFoundException</code>, then this method throws a
     * <code>ClassNotFoundException</code>; if
     * <code>RMIClassLoader.loadProxyClass</code> throws any other
     * exception, then this method throws that exception; otherwise,
     * this method returns the <code>Class</code> returned by
     * <code>RMIClassLoader.loadProxyClass</code>.
     *
     * @param codebase the list of URLs (separated by spaces) to load
     * classes from, or <code>null</code>
     *
     * @param interfaceNames the names of the interfaces for the proxy
     * class to implement
     *
     * @param defaultLoader the class loader value (possibly
     * <code>null</code>) to pass as the <code>defaultLoader</code>
     * argument to <code>RMIClassLoader.loadProxyClass</code>
     *
     * @param verifyCodebaseIntegrity if <code>true</code>, verify
     * that the codebase URLs provide content integrity
     *
     * @param verifierLoader the class loader value (possibly
     * <code>null</code>) to pass to
     * <code>Security.verifyCodebaseIntegrity</code>, if
     * <code>verifyCodebaseIntegrity</code> is <code>true</code>
     *
     * @return the <code>Class</code> object representing the loaded
     * dynamic proxy class
     *
     * @throws MalformedURLException if
     * <code>Security.verifyCodebaseIntegrity</code> or
     * <code>RMIClassLoader.loadProxyClass</code> throws a
     * <code>MalformedURLException</code>
     *
     * @throws ClassNotFoundException if
     * <code>RMIClassLoader.loadProxyClass</code> throws a
     * <code>ClassNotFoundException</code>
     *
     * @throws NullPointerException if <code>interfaceNames</code> is
     * <code>null</code> or if any element of
     * <code>interfaceNames</code> is <code>null</code>
     **/
    public static Class loadProxyClass(String codebase,
				       String[] interfaceNames,
				       ClassLoader defaultLoader,
				       boolean verifyCodebaseIntegrity,
				       ClassLoader verifierLoader)
	throws MalformedURLException, ClassNotFoundException
    {
	SecurityException verifyException = null;
	if (verifyCodebaseIntegrity && codebase != null) {
	    try {
		verifyIntegrity(codebase, verifierLoader);
	    } catch (SecurityException e) {
		verifyException = e;
		codebase = null;
	    }
	}
	try {
	    return RMIClassLoader.loadProxyClass(codebase, interfaceNames,
						 defaultLoader);
	} catch (ClassNotFoundException e) {
	    if (verifyException != null) {
		// assume that the verify exception is more important
		throw new ClassNotFoundException(e.getMessage(),
						 verifyException);
	    } else {
		throw e;
	    }
	}
    }

    /**
     * Wraps Security.verifyCodebaseIntegrity with caching for
     * performance.  (Perhaps such caching should be done by
     * Security.verifyCodebaseIntegrity instead.)
     **/
    private static void verifyIntegrity(String codebase,
					ClassLoader verifierLoader)
	throws MalformedURLException
    {
	/*
	 * Check if we've already verified the same codebase in this
	 * thread using the same verifierLoader value.
	 */
	Map verifierLoaderCache = (Map) perThreadCache.get();
	// defend against varying context class loader value of thread
	ClassLoader verifierLoaderKey =
	    (verifierLoader != null ? verifierLoader :
	     (ClassLoader) AccessController.doPrivileged(
		new PrivilegedAction() {
		    public Object run() {
			return Thread.currentThread().getContextClassLoader();
		    }
		}));
	Map verifiedCodebases =
	    (Map) verifierLoaderCache.get(verifierLoaderKey);
	if (verifiedCodebases != null &&
	    verifiedCodebases.containsKey(codebase))
	{
	    return;
	}

	Security.verifyCodebaseIntegrity(codebase, verifierLoader);

	/*
	 * Remember that we've verified this codebase in this thread
	 * with the given verifierLoader value.
	 */
	if (verifiedCodebases == null) {
	    verifiedCodebases = new WeakHashMap();
	    verifierLoaderCache.put(verifierLoaderKey, verifiedCodebases);
	}
	verifiedCodebases.put(codebase, new SoftReference(codebase));
	return;
    }

    private ClassLoading() { throw new AssertionError(); }
}
