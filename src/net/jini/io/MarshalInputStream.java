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

package net.jini.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.rmi.server.RMIClassLoaderSpi;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.jini.loader.ClassLoading;
import net.jini.security.Security;

/**
 * An extension of <code>ObjectInputStream</code> that implements the
 * dynamic class loading semantics of Java(TM) Remote Method
 * Invocation (Java RMI) argument and result
 * unmarshalling (using {@link ClassLoading}).  A
 * <code>MarshalInputStream</code> is intended to read data written by
 * a corresponding {@link MarshalOutputStream}.
 *
 * <p><code>MarshalInputStream</code> implements the input side of the
 * dynamic class loading semantics by overriding {@link
 * ObjectInputStream#resolveClass resolveClass} and {@link
 * ObjectInputStream#resolveProxyClass resolveProxyClass} to resolve
 * class descriptors in the stream using {@link ClassLoading#loadClass
 * ClassLoading.loadClass} and {@link ClassLoading#loadProxyClass
 * ClassLoading.loadProxyClass} (which, in turn, use {@link
 * RMIClassLoaderSpi#loadClass(String,String,ClassLoader)
 * RMIClassLoaderSpi.loadClass} and {@link
 * RMIClassLoaderSpi#loadProxyClass(String,String[],ClassLoader)
 * RMIClassLoaderSpi.loadProxyClass}), optionally with codebase
 * annotation strings written by a <code>MarshalOutputStream</code>.
 *
 * <p>By default, a <code>MarshalInputStream</code> ignores all
 * codebase annotation strings, instead using a codebase value of
 * <code>null</code> when loading classes.  Codebase annotation
 * strings will only be used by a <code>MarshalInputStream</code>
 * after its {@link #useCodebaseAnnotations useCodebaseAnnotations}
 * method has been invoked.
 *
 * <p><code>MarshalInputStream</code> supports optional verification
 * that all codebase annotation URLs that are used to load classes
 * resolved by the stream provide content integrity (see {@link
 * Security#verifyCodebaseIntegrity
 * Security.verifyCodebaseIntegrity}).  Whether or not a particular
 * <code>MarshalInputStream</code> instance verifies that codebase
 * annotation URLs provide content integrity is determined by the
 * <code>verifyCodebaseIntegrity</code> constructor argument.  See
 * {@link ClassLoading#loadClass ClassLoading.loadClass} and {@link
 * ClassLoading#loadProxyClass ClassLoading.loadProxyClass} for
 * details of how codebase integrity verification is performed.
 *
 * <p><code>MarshalInputStream</code> reads class annotations from its
 * own stream; a subclass can override the {@link #readAnnotation
 * readAnnotation} method to read the class annotations from a
 * different location.
 *
 * <p>A <code>MarshalInputStream</code> is not guaranteed to be
 * safe for concurrent use by multiple threads.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public class MarshalInputStream
    extends ObjectInputStream implements ObjectStreamContext
{
    /**
     * maps keywords for primitive types and void to corresponding
     * Class objects
     **/
    private static final Map specialClasses = new HashMap();
    static {
	specialClasses.put("boolean", boolean.class);
	specialClasses.put("byte", byte.class);
	specialClasses.put("char", char.class);
	specialClasses.put("short", short.class);
	specialClasses.put("int", int.class);
	specialClasses.put("long", long.class);
	specialClasses.put("float", float.class);
	specialClasses.put("double", double.class);
	specialClasses.put("void", void.class);
    }

    /** context for ObjectStreamContext implementation */
    private final Collection context;

    /**
     * value to pass as the "default loader" argument to loadClass and
     * loadProxyClass
     **/
    private final ClassLoader defaultLoader;

    /** true if this stream verifies codebase integrity */
    private final boolean verifyCodebaseIntegrity;

    /** loader to pass to Security.verifyCodebaseIntegrity */
    private final ClassLoader verifierLoader;

    /**
     * if false, pass null codebase values to loadClass and
     * loadProxyClass methods; if true, pass codebase values from
     * stream class annotations
     **/
    private boolean usingCodebaseAnnotations = false;

    /**
     * Creates a new <code>MarshalInputStream</code> that reads
     * marshalled data from the specified underlying
     * <code>InputStream</code>.
     *
     * <p>This constructor passes <code>in</code> to the superclass
     * constructor that has an <code>InputStream</code> parameter.
     *
     * <p><code>defaultLoader</code> will be passed as the
     * <code>defaultLoader</code> argument to {@link
     * ClassLoading#loadClass ClassLoading.loadClass} and {@link
     * ClassLoading#loadProxyClass ClassLoading.loadProxyClass}
     * whenever those methods are invoked by {@link #resolveClass
     * resolveClass} and {@link #resolveProxyClass resolveProxyClass}.
     *
     * <p>If <code>verifyCodebaseIntegrity</code> is
     * <code>true</code>, then the created stream will verify that all
     * codebase annotation URLs that are used to load classes resolved
     * by the stream provide content integrity, and whenever {@link
     * Security#verifyCodebaseIntegrity
     * Security.verifyCodebaseIntegrity} is invoked to enforce that
     * verification, <code>verifierLoader</code> will be passed as the
     * <code>loader</code> argument.  See {@link
     * ClassLoading#loadClass ClassLoading.loadClass} and {@link
     * ClassLoading#loadProxyClass ClassLoading.loadProxyClass} for
     * details of how codebase integrity verification is performed.
     *
     * <p><code>context</code> will be used as the return value of the
     * created stream's {@link #getObjectStreamContext
     * getObjectStreamContext} method.
     *
     * @param in the input stream to read marshalled data from
     *
     * @param defaultLoader the class loader value (possibly
     * <code>null</code>) to pass as the <code>defaultLoader</code>
     * argument to <code>ClassLoading</code> methods
     *
     * @param verifyCodebaseIntegrity if <code>true</code>, this
     * stream will verify that codebase annotation URLs used to load
     * classes resolved by this stream provide content integrity
     *
     * @param verifierLoader the class loader value (possibly
     * <code>null</code>) to pass to
     * <code>Security.verifyCodebaseIntegrity</code>, if
     * <code>verifyCodebaseIntegrity</code> is <code>true</code>
     *
     * @param context the collection of context information objects to
     * be returned by this stream's {@link #getObjectStreamContext
     * getObjectStreamContext} method
     *
     * @throws IOException if the superclass's constructor throws an
     * <code>IOException</code>
     *
     * @throws SecurityException if the superclass's constructor
     * throws a <code>SecurityException</code>
     *
     * @throws NullPointerException if <code>in</code> or
     * <code>context</code> is <code>null</code>
     **/
    public MarshalInputStream(InputStream in,
			      ClassLoader defaultLoader,
			      boolean verifyCodebaseIntegrity,
			      ClassLoader verifierLoader,
			      Collection context)
	throws IOException
    {	      
	super(in);
	if (context == null) {
	    throw new NullPointerException();
	}
	this.defaultLoader = defaultLoader;
	this.verifyCodebaseIntegrity = verifyCodebaseIntegrity;
	this.verifierLoader = verifierLoader;
	this.context = context;
        AccessController.doPrivileged(new PrivilegedAction<Object>(){

            @Override
            public Object run() {
                enableResolveObject(true);
                return null;
            }
            
        });
        
    }

    /**
     * Enables the use of codebase annotation strings written by the
     * corresponding <code>MarshalOutputStream</code>.
     *
     * <p>If this method has not been invoked on this stream, then the
     * <code>resolveClass</code> and <code>resolveProxyClass</code>
     * methods ignore the class annotation strings and just use a
     * <code>null</code> codebase value when loading classes.  After
     * this method has been invoked, then the
     * <code>resolveClass</code> and <code>resolveProxyClass</code>
     * methods use the class annotation strings as codebase values.
     **/
    public void useCodebaseAnnotations() {
	usingCodebaseAnnotations = true;
    }

    /**
     * Returns the collection of context information objects that
     * was passed to this stream's constructor.
     **/
    public Collection getObjectStreamContext() {
	return context;
    }

    /**
     * Resolves the appropriate {@link Class} object for the stream
     * class descriptor <code>classDesc</code>.
     *
     * <p><code>MarshalInputStream</code> implements this method as
     * follows:
     *
     * <p>This method first invokes this stream's {@link
     * #readAnnotation readAnnotation} method to read the annotation
     * string value (possibly <code>null</code>) for the class
     * descriptor.  If <code>readAnnotation</code> throws an
     * exception, then this method throws that exception.  Otherwise,
     * a codebase value is chosen as follows: if the {@link
     * #useCodebaseAnnotations useCodebaseAnnotations} method has been
     * invoked on this stream, then the codebase value chosen is the
     * value that was returned by <code>readAnnotation</code>;
     * otherwise, the codebase value chosen is <code>null</code>.
     *
     * <p>This method then invokes {@link ClassLoading#loadClass
     * ClassLoading.loadClass} with the chosen codebase value as the
     * first argument, the name of the class described by
     * <code>classDesc</code> as the second argument, and the
     * <code>defaultLoader</code>,
     * <code>verifyCodebaseIntegrity</code>, and
     * <code>verifierLoader</code> values that were passed to this
     * stream's constructor as the third, fourth, and fifth arguments.
     * If <code>ClassLoading.loadClass</code> throws a
     * <code>ClassNotFoundException</code> and the name of the class
     * described by <code>classDesc</code> equals the Java
     * programming language keyword for a primitive type or
     * <code>void</code>, then this method returns the
     * <code>Class</code> corresponding to that primitive type or
     * <code>void</code> ({@link Integer#TYPE} for <code>int</code>,
     * {@link Void#TYPE} for <code>void</code>, and so forth).
     * Otherwise, if <code>ClassLoading.loadClass</code> throws an
     * exception, this method throws that exception, and if it returns
     * normally, this method returns the <code>Class</code> returned
     * by <code>ClassLoading.loadClass</code>.
     *
     * @param classDesc the stream class descriptor to resolve
     *
     * @return the resolved class
     *
     * @throws IOException if <code>readAnnotation</code> throws an
     * <code>IOException</code>, or if
     * <code>ClassLoading.loadClass</code> throws a
     * <code>MalformedURLException</code>
     *
     * @throws ClassNotFoundException if <code>readAnnotation</code>
     * or <code>ClassLoading.loadClass</code> throws a
     * <code>ClassNotFoundException</code>
     *
     * @throws NullPointerException if <code>classDesc</code> is
     * <code>null</code>
     **/
    protected Class resolveClass(ObjectStreamClass classDesc)
	throws IOException, ClassNotFoundException
    {
	if (classDesc == null) {
	    throw new NullPointerException();
	}

	// must always consume annotation written by MarshalOutputStream
	String annotation = readAnnotation();
	String codebase = usingCodebaseAnnotations ? annotation : null;

	String name = classDesc.getName();
	try {
	    return ClassLoading.loadClass(codebase,
					  name,
					  defaultLoader,
					  verifyCodebaseIntegrity,
					  verifierLoader);
	} catch (ClassNotFoundException e) {
	    Class c = (Class) specialClasses.get(name);
	    if (c != null) {
		return c;
	    } else {
		throw e;
	    }
	}
    }

    /**
     * Resolves the appropriate {@link Class} object for the proxy
     * class described by the interface names
     * <code>interfaceNames</code>.
     *
     * <p><code>MarshalInputStream</code> implements this method as
     * follows:
     *
     * <p>This method first invokes this stream's {@link
     * #readAnnotation readAnnotation} method to read the annotation
     * string value (possibly <code>null</code>) for the class
     * descriptor.  If <code>readAnnotation</code> throws an
     * exception, then this method throws that exception.  Otherwise,
     * a codebase value is chosen as follows: if the {@link
     * #useCodebaseAnnotations useCodebaseAnnotations} method has been
     * invoked on this stream, then the codebase value chosen is the
     * value that was returned by <code>readAnnotation</code>;
     * otherwise, the codebase value chosen is <code>null</code>.
     *
     * <p>This method then invokes {@link ClassLoading#loadProxyClass
     * ClassLoading.loadProxyClass} with the chosen codebase value as
     * the first argument, <code>interfaceNames</code> as the second
     * argument, and the <code>defaultLoader</code>,
     * <code>verifyCodebaseIntegrity</code>, and
     * <code>verifierLoader</code> values that were passed to this
     * stream's constructor as the third, fourth, and fifth arguments.
     * If <code>ClassLoading.loadProxyClass</code> throws an
     * exception, then this method throws that exception.  Otherwise,
     * this method returns the <code>Class</code> returned by
     * <code>ClassLoading.loadProxyClass</code>.
     *
     * @param interfaceNames the list of interface names that were
     * deserialized in the proxy class descriptor
     *
     * @return the resolved dynamic proxy class
     *
     * @throws IOException if <code>readAnnotation</code> throws an
     * <code>IOException</code>, or if
     * <code>ClassLoading.loadProxyClass</code> throws a
     * <code>MalformedURLException</code>
     *
     * @throws ClassNotFoundException if <code>readAnnotation</code>
     * or <code>ClassLoading.loadProxyClass</code> throws a
     * <code>ClassNotFoundException</code>
     *
     * @throws NullPointerException if <code>interfaceNames</code> is
     * <code>null</code> or if any element of
     * <code>interfaceNames</code> is <code>null</code>
     **/
    protected Class resolveProxyClass(String[] interfaceNames)
	throws IOException, ClassNotFoundException
    {
	for (int i = 0; i < interfaceNames.length; i++) {
	    if (interfaceNames[i] == null) {
		throw new NullPointerException();
	    }
	}

	// must always consume annotation written by MarshalOutputStream
	String annotation = readAnnotation();
	String codebase = usingCodebaseAnnotations ? annotation : null;

	return ClassLoading.loadProxyClass(codebase,
					   interfaceNames,
					   defaultLoader,
					   verifyCodebaseIntegrity,
					   verifierLoader);
    }

    /**
     * Reads and returns a class annotation string value (possibly
     * <code>null</code>) that was written by a corresponding
     * <code>MarshalOutputStream</code> implementation.
     *
     * <p><code>MarshalInputStream</code> implements this method to
     * just read the annotation value from this stream using {@link
     * ObjectInputStream#readObject readObject}, and if
     * <code>readObject</code> returns a non-<code>null</code> value
     * that is not a <code>String</code>, an {@link
     * InvalidObjectException} is thrown.
     *
     * <p>A subclass can override this method to read the annotation
     * from a different location.
     *
     * @return the class annotation string value read (possibly
     * <code>null</code>)
     *
     * @throws IOException if an I/O exception occurs reading the
     * annotation
     *
     * @throws ClassNotFoundException if a
     * <code>ClassNotFoundException</code> occurs reading the
     * annotation
     **/
    protected String readAnnotation()
	throws IOException, ClassNotFoundException
    {
	try {
	    return (String) readObject();
	} catch (ClassCastException e) {
	    InvalidObjectException ioe =
		new InvalidObjectException("annotation not String or null");
	    ioe.initCause(e);
	    throw ioe;
	}
    }
}
