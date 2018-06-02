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

package org.apache.river.api.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.jini.export.DynamicProxyCodebaseAccessor;
import net.jini.export.ProxyAccessor;
import net.jini.io.MarshalFactory;
import net.jini.io.MarshalInstanceInput;
import net.jini.io.MarshalInstanceOutput;
import net.jini.io.MarshalledInstance;
import net.jini.io.ObjectStreamContext;

/**
 * Implementation of MarshalledInstance that performs input validation 
 * during un-marshaling.
 * 
 * Note that this implementation doesn't replace the stored object instance
 * if it's an instance of {@link ProxyAccessor}, but will replace any {@link ProxyAccessor}
 * references it contains.
 * 
 * @author peter
 */
@AtomicSerial
public final class AtomicMarshalledInstance extends MarshalledInstance {
    
    AtomicMarshalledInstance(AtomicSerial.GetArg arg) throws IOException{
	super(arg);
    }
    
    /**
     * Creates a new <code>MarshalledInstance</code> from an
     * existing <code>MarshalledObject</code>. An object equivalent
     * to the object contained in the passed <code>MarshalledObject</code>
     * will be contained in the new <code>AtomicMarshalledInstance</code>.
     * <p>
     * The object contained in the passed <code>MarshalledObject</code>
     * will not be unmarshalled as part of this call.
     * <p>
     * Note that an AtomicMarshalledInstance, converted to a MarshalledObject
     * can be deserialized, without support for using atomic input validation.
     * A MarshalledObject converted to an AtomicMarshalledObject can only be
     * deserialized if it was originally created as an AtomicMarshalledInstance.
     * This functionality only exists for backward compatibility reasons.
     * 
     *
     * @param mo The <code>MarshalledObject</code> that contains
     *        the object the new <code>MarshalledInstance</code> should
     *        contain
     * @throws NullPointerException if <code>mo</code> is <code>null</code>
     */
    public AtomicMarshalledInstance(java.rmi.MarshalledObject mo) {
	super(mo);
    }
    
    /**
     * Creates a new <code>MarshalledInstance</code> that contains the
     * marshalled representation of the current state of the supplied
     * object. The object is serialized with the semantics defined by
     * <code>AtomicMarshalOutputStream</code>. The output stream used to marshal the
     * object implements {@link ObjectStreamContext} and returns an empty
     * collection from its {@link ObjectStreamContext#getObjectStreamContext
     * getObjectStreamContext} method.
     *
     * @param obj The Object to be contained in the new 
     *          <code>MarshalledInstance</code>
     * @throws IOException if the object cannot be serialized
     */
    public AtomicMarshalledInstance(Object obj) throws IOException {
	this(obj, Collections.EMPTY_SET);
    }
    
    /**
     * Creates a new <code>AtomicMarshalledInstance</code> that contains the
     * marshalled representation of the current state of the supplied
     * object. The object is serialized with the semantics defined by
     * <code>AtomicMarshalOutputStream</code>. The output stream used to marshal the
     * object implements {@link ObjectStreamContext} and returns the given
     * collection from its {@link ObjectStreamContext#getObjectStreamContext
     * getObjectStreamContext} method.
     *
     * @param obj The Object to be contained in the new 
     *          <code>AtomicMarshalledInstance</code>
     * @param context the collection of context information objects
     * @throws IOException if the object cannot be serialized
     * @throws NullPointerException if <code>context</code> is <code>null</code>
     */
    public AtomicMarshalledInstance(Object obj, final Collection context)
	throws IOException
    {
	super(obj, context, new AtomicMarshalFactoryInstance(getLoader(obj)));
    }
    
    @Override
    protected final MarshalFactory getMarshalFactory(){
	return new AtomicMarshalFactoryInstance(null);
    }
    
    static ClassLoader getLoader(final Object o){
	if (o == null) return null;
	return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>(){

	    public ClassLoader run() {
		return o.getClass().getClassLoader();
	    }
	    
	});
    }
    
    static class AtomicMarshalFactoryInstance implements MarshalFactory {
	private final ClassLoader defaultOutLoader;
	
	AtomicMarshalFactoryInstance(ClassLoader defaultOutLoader){
	    this.defaultOutLoader = defaultOutLoader;
	}

	@Override
	public MarshalInstanceInput createMarshalInput(final InputStream objIn,
		final InputStream locIn, 
		final ClassLoader defaultLoader,
		final boolean verifyCodebaseIntegrity,
		final ClassLoader verifierLoader,
		final Collection context) throws IOException 
	{
	    try {
		return AccessController.doPrivileged(
		    new PrivilegedExceptionAction<MarshalInstanceInput>(){
		    
			public MarshalInstanceInput run() throws IOException {
			    return new AtomicMarshalledInstanceInputStream(
				    objIn,
				    locIn,
				    defaultLoader,
				    verifyCodebaseIntegrity,
				    verifierLoader,
				    context
			    );
			}

		    }
		);
	    } catch (PrivilegedActionException ex) {
		Exception e = ex.getException();
		if (e instanceof IOException) throw (IOException) e;
		if (e instanceof RuntimeException) throw (RuntimeException) e;
		throw new IOException(
		    "Unknown exception type throw while creating AtomicMarshalInputStream",
		    e);
	    }
	    
	}

	@Override
	public MarshalInstanceOutput createMarshalOutput(
		final OutputStream objOut, 
		final OutputStream locOut, 
		final Collection context) throws IOException 
	{
	    try {
		return AccessController.doPrivileged(
		    new PrivilegedExceptionAction<MarshalInstanceOutput>(){
		    
			public MarshalInstanceOutput run() throws IOException {
			    return new AtomicMarshalledInstanceOutputStream(
				    objOut, defaultOutLoader, context
			    );
			}

		    }
		);
	    } catch (PrivilegedActionException ex) {
		Exception e = ex.getException();
		if (e instanceof IOException) throw (IOException) e;
		if (e instanceof RuntimeException) throw (RuntimeException) e;
		throw new IOException(
		    "Unknown exception type throw while creating AtomicMarshalInputStream",
		    e);
	    }
	    
	}
    }
	
    private static class AtomicMarshalledInstanceOutputStream
        extends AtomicMarshalOutputStream implements MarshalInstanceOutput
    {
	/** The stream on which location objects are written. */
//	private final ObjectOutputStream locOut;
 
	/** <code>true</code> if non-<code>null</code> annotations are
	 *  written.
	 */
	private final boolean hadAnnotations;
	private int count;

	/**
	 * Creates a new <code>AtomicMarshalledObjectOutputStream</code> whose
	 * non-location bytes will be written to <code>objOut</code> and whose
	 * location annotations (if any) will be written to
	 * <code>locOut</code>.
	 */
	public AtomicMarshalledInstanceOutputStream(OutputStream objOut, ClassLoader loader, Collection context)
	    throws IOException
	{
	    super(objOut, loader, context, true);
//	    this.locOut = new ObjectOutputStream(locOut);
	    hadAnnotations = false;
	    super.enableReplaceObject(true);
	    count = 0;
	}
	
	@Override
	protected Object replaceObject(Object obj) throws IOException {
	    try {
		// Never replace the first object written if it's a ProxyAccessor,
		// this is to allow the serial form of a smart proxy to be
		// stored by ProxyAccessorSerializer.
		if (count == 0 
		    && (obj instanceof ProxyAccessor 
		    || obj instanceof DynamicProxyCodebaseAccessor)) return obj;
		return super.replaceObject(obj);
	    } finally {
		count++;
	    }
	}
 
	/**
	 * Returns <code>true</code> if any non-<code>null</code> location
	 * annotations have been written to this stream.
	 */
	@Override
	public boolean hadAnnotations() {
	    return hadAnnotations;
	}
 
	/**
	 * Overrides <code>MarshalOutputStream.writeAnnotation</code>
	 * implementation to write annotations to the location stream.
	 */
	@Override
	public void writeAnnotation(String loc) throws IOException {
//	    hadAnnotations |= (loc != null);
//	    locOut.writeObject(loc);
	}

	@Override
	public void flush() throws IOException {
	    super.flush();
//	    locOut.flush();
	}
    }
    
     /**
     * The counterpart to <code>AtomicMarshalledInstanceOutputStream</code>.
     *   
     * @see MarshalledInstanceOutputStream
     */  
    private static class AtomicMarshalledInstanceInputStream
        extends AtomicMarshalInputStream implements MarshalInstanceInput
    {
	/**
	 * The stream from which annotations will be read.  If this is
	 * <code>null</code>, then all annotations were <code>null</code>.
	 */
//	private final ObjectInputStream locIn;
 
	/**
	 * Creates a new <code>AtomicMarshalledObjectInputStream</code> that
	 * reads its objects from <code>objIn</code> and annotations
	 * from <code>locIn</code>.  If <code>locIn</code> is
	 * <code>null</code>, then all annotations will be
	 * <code>null</code>.
	 */
	AtomicMarshalledInstanceInputStream(InputStream objIn,
				      InputStream locIn,
				      ClassLoader defaultLoader,
				      boolean verifyCodebaseIntegrity,
				      ClassLoader verifierLoader,
				      Collection context)
	    throws IOException
	{
	    super(objIn,
		  defaultLoader,
		  verifyCodebaseIntegrity,
		  verifierLoader,
		  context);
//	    this.locIn = (locIn == null ? null : new ObjectInputStream(locIn));
	}
 
	/**
	 * Overrides <code>MarshalInputStream.readAnnotation</code> to
	 * return locations from the stream we were given, or <code>null</code>
	 * if we were given a <code>null</code> location stream.
	 */
	@Override
	protected String readAnnotation()
	    throws IOException, ClassNotFoundException
	{
//	    return (locIn == null ? null : (String)locIn.readObject());
	    return null;
	}
    }   
}
