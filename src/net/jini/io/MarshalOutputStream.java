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
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.rmi.server.RMIClassLoaderSpi;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import net.jini.loader.ClassLoading;
/**
 * An extension of <code>ObjectOutputStream</code> that implements the
 * dynamic class loading semantics of Java(TM) Remote Method Invocation
 * (Java RMI) argument and result
 * marshalling (using {@link ClassLoading}).  A
 * <code>MarshalOutputStream</code> writes data that is intended to be
 * written by a corresponding {@link MarshalInputStream}.
 *
 * <p><code>MarshalOutputStream</code> implements the output side of
 * the dynamic class loading semantics by overriding {@link
 * ObjectOutputStream#annotateClass annotateClass} and {@link
 * ObjectOutputStream#annotateProxyClass annotateProxyClass} to
 * annotate class descriptors in the stream with codebase strings
 * obtained using {@link RMIClassLoaderSpi#getClassAnnotation
 * RMIClassLoaderSpi.getClassAnnotation}.
 *
 * <p><code>MarshalOutputStream</code> writes class annotations to its
 * own stream; a subclass may override the {@link #writeAnnotation
 * writeAnnotation} method to write the class annotations to a
 * different location.
 *
 * <p><code>MarshalOutputStream</code> does not modify the stream
 * protocol version of its instances' superclass state (see {@link
 * ObjectOutputStream#useProtocolVersion
 * ObjectOutputStream.useProtocolVersion}).
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 **/
public class MarshalOutputStream
    extends ObjectOutputStream
    implements ObjectStreamContext
{
    /** context for ObjectStreamContext implementation */
    private final Collection context;

    /**
     * Creates a new <code>MarshalOutputStream</code> that writes
     * marshalled data to the specified underlying
     * <code>OutputStream</code>.
     *
     * <p>This constructor passes <code>out</code> to the superclass
     * constructor that has an <code>OutputStream</code> parameter.
     *
     * <p><code>context</code> will be used as the return value of the
     * created stream's {@link #getObjectStreamContext
     * getObjectStreamContext} method.
     *
     * @param out the output stream to write marshalled data to
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
     * @throws NullPointerException if <code>out</code> or
     * <code>context</code> is <code>null</code>
     **/
    public MarshalOutputStream(OutputStream out, Collection context)
	throws IOException
    {
	super(out);
	if (context == null) {
	    throw new NullPointerException();
	}
	this.context = context;
        
        AccessController.doPrivileged(new PrivilegedAction<Object>(){

            @Override
            public Object run() {
                enableReplaceObject(true);
                return null;
            }
            
        });
    }

    /**
     * Returns the collection of context information objects that
     * was passed to this stream's constructor.
     **/
    public Collection getObjectStreamContext() {
	return context;
    }

    /**
     * Annotates the stream descriptor for the class <code>cl</code>.
     *
     * <p><code>MarshalOutputStream</code> implements this method as
     * follows:
     *
     * <p>This method invokes {@link RMIClassLoaderSpi#getClassAnnotation
     * RMIClassLoaderSpi.getClassAnnotation} with <code>cl</code> to get
     * the appropriate class annotation string value (possibly
     * <code>null</code>), and then it invokes this stream's {@link
     * #writeAnnotation writeAnnotation} method with that string to
     * record the annotation.
     *
     * @param cl the class to annotate
     *
     * @throws IOException if <code>writeAnnotation</code> throws an
     * <code>IOException</code>
     *
     * @throws NullPointerException if <code>cl</code> is
     * <code>null</code>
     **/
    protected void annotateClass(Class cl) throws IOException {
	writeAnnotation(ClassLoading.getClassAnnotation(cl));
    }

    /**
     * Annotates the stream descriptor for the proxy class
     * <code>cl</code>.
     *
     * <p><code>MarshalOutputStream</code> implements this method as
     * follows:
     *
     * <p>This method invokes {@link RMIClassLoaderSpi#getClassAnnotation
     * RMIClassLoaderSpi.getClassAnnotation} with <code>cl</code> to get
     * the appropriate class annotation string value (possibly
     * <code>null</code>), and then it invokes this stream's {@link
     * #writeAnnotation writeAnnotation} method with that string to
     * record the annotation.
     *
     * @param cl the proxy class to annotate
     *
     * @throws IOException if <code>writeAnnotation</code> throws an
     * <code>IOException</code>
     *
     * @throws NullPointerException if <code>cl</code> is
     * <code>null</code>
     **/
    protected void annotateProxyClass(Class cl) throws IOException {
	writeAnnotation(ClassLoading.getClassAnnotation(cl));
    }

    /**
     * Writes a class annotation string value (possibly
     * <code>null</code>) to be read by a corresponding
     * <code>MarshalInputStream</code> implementation.
     *
     * <p><code>MarshalOutputStream</code> implements this method to
     * just write the annotation value to this stream using {@link
     * ObjectOutputStream#writeObject writeObject}.
     *
     * <p>A subclass can override this method to write the annotation
     * to a different location.
     *
     * @param annotation the class annotation string value (possibly
     * <code>null</code>) to write
     *
     * @throws IOException if I/O exception occurs writing the
     * annotation
     **/
    protected void writeAnnotation(String annotation) throws IOException {
	writeObject(annotation);
    }
}
