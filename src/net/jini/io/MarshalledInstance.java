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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import net.jini.io.context.IntegrityEnforcement;

/*
 * Implementation note: This class uses the helper class
 * MarshalledObject that is in this package. To avoid confusion
 * with java.rmi.MarshalledObject the fully qualified class names
 * are used for both classes.
 */

/**
 * A <code>MarshalledInstance</code> contains an object in serialized
 * form. The contained object can be deserialized on demand when
 * explicitly requested. This allows an object to be sent from one VM
 * to another in a way that allows the receiver to control when and if
 * the object is deserialized.
 * <p>
 * The contained object is specified at construction time and can
 * either be provided in unserialized or serialized form. If provided
 * in unserialized form it will be serialized during construction
 * with the serialization semantics defined by
 * <code>MarshalOutputStream</code>. In particular, classes are annotated
 * with a codebase URL from which the class can be loaded (if available).
 * <p>
 * If the <code>MarshalledInstance</code> needs to deserialize the
 * contained object then the contained object will be deserialized with the
 * deserialization semantics defined by <code>MarshalInputStream</code>.
 * In particular, the codebase annotations associated with the contained
 * object may be used to load classes referenced by the contained object.
 * <p>
 * <code>MarshalledInstance</code> provides functionality similar to
 * <code>java.rmi.MarshalledObject</code>, but additionally provides
 * for the verification of codebase integrity. Unlike
 * <code>java.rmi.MarshalledObject</code>, it does not perform remote
 * object-to-stub replacement.
 *
 * @author Sun Microsystems, Inc.
 * @since 2.0
 */
public class MarshalledInstance<T> implements Serializable {

    /**
     * @serial Bytes of serialized representation.  If <code>objBytes</code> is
     * <code>null</code> then the object marshalled was a <code>null</code>
     * reference.
     */  
    private byte[] objBytes = null;
 
    /**
     * @serial Bytes of location annotations, which are ignored by
     * <code>equals</code>.  If <code>locBytes</code> is null, there were no
     * non-<code>null</code> annotations during marshalling.
     */  
    private byte[] locBytes = null;
 
    /**
     * @serial Stored hash code of contained object.
     *   
     * @see #hashCode
     */  
    private int hash;

    static final long serialVersionUID = -5187033771082433496L;
    
    MarshalledInstance(net.jini.io.MarshalledObject<T> mo){
        if ( mo == null) throw new NullPointerException("MarshalledObject was null");
        // for some reason objBytes.clone() throws a null pointer exception.
        objBytes = Arrays.copyOf(mo.objBytes, mo.objBytes.length);
        locBytes = mo.locBytes;
        hash = mo.hash;
    }
    
    /**
     * Creates a new <code>MarshalledInstance</code> that contains the
     * marshalled representation of the current state of the supplied
     * object. The object is serialized with the semantics defined by
     * <code>MarshalOutputStream</code>. The output stream used to marshal the
     * object implements {@link ObjectStreamContext} and returns an empty
     * collection from its {@link ObjectStreamContext#getObjectStreamContext
     * getObjectStreamContext} method.
     *
     * @param obj The Object to be contained in the new 
     *          <code>MarshalledInstance</code>
     * @throws IOException if the object cannot be serialized
     */
    public MarshalledInstance(T obj) throws IOException {
	this(obj, Collections.EMPTY_SET);
    }

    /**
     * Creates a new <code>MarshalledInstance</code> that contains the
     * marshalled representation of the current state of the supplied
     * object. The object is serialized with the semantics defined by
     * <code>MarshalOutputStream</code>. The output stream used to marshal the
     * object implements {@link ObjectStreamContext} and returns the given
     * collection from its {@link ObjectStreamContext#getObjectStreamContext
     * getObjectStreamContext} method.
     *
     * @param obj The Object to be contained in the new 
     *          <code>MarshalledInstance</code>
     * @param context the collection of context information objects
     * @throws IOException if the object cannot be serialized
     * @throws NullPointerException if <code>context</code> is <code>null</code>
     */
    public MarshalledInstance(T obj, Collection context)
	throws IOException
    {
	if (context == null)
	    throw new NullPointerException();

	if (obj == null) {
	    hash = 13;		// null hash for java.rmi.MarshalledObject
	    return;
	}
	ByteArrayOutputStream bout = new ByteArrayOutputStream();
	ByteArrayOutputStream lout = new ByteArrayOutputStream();
	MarshalledInstanceOutputStream out =
			new MarshalledInstanceOutputStream(bout, lout, context);
	out.writeObject(obj);
	out.flush();
	objBytes = bout.toByteArray();
	// locBytes is null if no annotations
	locBytes = (out.hadAnnotations() ? lout.toByteArray() : null);

	// Calculate hash from the marshalled representation of object
	// so the hashcode will be comparable when sent between VMs.
	//
	// Note: This calculation must match the calculation in
	//	 java.rmi.MarshalledObject since we use this hash
	//	 in the converted MarshalledObject. The reverse is
	//	 also true in that we use the MarshalledObject's
	//	 hash for our hash. (see the MarshalledInstance(
	//	 MarshalledObject) constructor)
	//
	int h = 0;
	for (int i = 0; i < objBytes.length; i++) {
	    h = 31 * h + objBytes[i];
	}
	hash = h;
    }

    /**
     * Creates a new <code>MarshalledInstance</code> from an
     * existing <code>MarshalledObject</code>. An object equivalent
     * to the object contained in the passed <code>MarshalledObject</code>
     * will be contained in the new <code>MarshalledInstance</code>.
     * <p>
     * The object contained in the passed <code>MarshalledObject</code>
     * will not be unmarshalled as part of this call.
     *
     * @param mo The <code>MarshalledObject</code> that contains
     *        the object the new <code>MarshalledInstance</code> should
     *        contain
     * @throws NullPointerException if <code>mo</code> is <code>null</code>
     * @deprecated As of Release 2.2.0 replaced by {@link Convert}
     */
    @Deprecated
    public MarshalledInstance(java.rmi.MarshalledObject<T> mo) {

	if (mo == null)
	    throw new NullPointerException();

	// To extract the java.rmi.MarshalledObject's fields we
	// convert the mo into a net.jini.io.MarshalledObject.
	// (See resolveClass() in FromMOInputStream) The private
	// version of MarshalledObject allows access to the needed
	// fields.
	//
        Convert<T> convert = new Convert<T>();
	net.jini.io.MarshalledObject<T> privateMO = 
                convert.toJiniMarshalledObject(mo);
	objBytes = privateMO.objBytes;
	locBytes = privateMO.locBytes;
	hash = privateMO.hash;
    }
    
    /**
     * Creates a new <code>MarshalledObject</code> that will
     * contain an object equivalent to the object contained
     * in this <code>MarshalledInstance</code> object.
     * <p>
     * The object contained in this <code>MarshalledInstance</code>
     * object will not be unmarshalled as part of this call.
     * @deprecated As of Release 2.2.0 replaced by {@link Convert}
     * @return A new <code>MarshalledObject</code> which
     *        contains an object equivalent to the object
     *        contained in this <code>MarshalledInstance</code>
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public java.rmi.MarshalledObject<T> convertToMarshalledObject() {
        Convert convert = Convert.getInstance();
        return convert.toRmiMarshalledObject(this);
    }
    
    MarshalledObject<T> asMarshalledObject(){
        MarshalledObject<T> mo = new MarshalledObject<T>();
        // Don't worry about defensive copies, this is package private.
        mo.objBytes = objBytes;
        mo.locBytes = locBytes;
        mo.hash = hash;
        return mo;
    }
    
    /**
     * Returns a new copy of the contained object. Deserialization is
     * performed with the semantics defined by <code>MarshalInputStream</code>.
     * The input stream used to unmarshal the object implements {@link
     * ObjectStreamContext} and returns a collection from its {@link
     * ObjectStreamContext#getObjectStreamContext getObjectStreamContext}
     * method which contains a single element of type {@link
     * IntegrityEnforcement}; the {@link IntegrityEnforcement#integrityEnforced
     * integrityEnforced} method of this element returns the specified
     * <code>verifyCodebaseIntegrity</code> value.
     * <p>MarshalledInstance</code> implements this method by calling
     * <code>{@link #get(ClassLoader, boolean, ClassLoader, Collection)
     * get}(null, verifyCodebaseIntegrity, null, null)</code>.
     *
     * @param verifyCodebaseIntegrity if <code>true</code> then
     *        codebase integrity is verified, otherwise code base
     *        integrity is not verified
     * @return a new copy of the contained object
     * @throws IOException if an 
     *         <code>IOException</code> occurs while deserializing the
     *         object from its internal representation
     * @throws ClassNotFoundException if any classes necessary
     *         for reconstructing the contained object can not
     *         be found or if <code>verifyCodebaseIntegrity</code>
     *         is <code>true</code> and the integrity of the
     *         contained object's codebase cannot be confirmed
     */
    public T get(final boolean verifyCodebaseIntegrity) 
	throws IOException, ClassNotFoundException 
    {
	return get(null, verifyCodebaseIntegrity, null, null);
    }

    /**
     * Returns a new copy of the contained object. Deserialization is
     * performed with the semantics defined by <code>MarshalInputStream</code>.
     * If <code>context</code> is not <code>null</code>
     * the input stream used to unmarshal the object implements {@link
     * ObjectStreamContext} and returns the given collection from its {@link
     * ObjectStreamContext#getObjectStreamContext getObjectStreamContext}
     * method.
     * <p>If <code>context</code> is <code>null</code>
     * the input stream used to unmarshal the object implements {@link
     * ObjectStreamContext} and returns a collection from its {@link
     * ObjectStreamContext#getObjectStreamContext getObjectStreamContext}
     * method which contains a single element of type {@link
     * IntegrityEnforcement}; the {@link IntegrityEnforcement#integrityEnforced
     * integrityEnforced} method of this element returns the specified
     * <code>verifyCodebaseIntegrity</code> value.
     *
     * @param defaultLoader the class loader value (possibly
     *	      <code>null</code>) to pass as the <code>defaultLoader</code>
     *        argument to <code>RMIClassLoader</code> methods
     * @param verifyCodebaseIntegrity if <code>true</code> then
     *        codebase integrity is verified, otherwise code base
     *        integrity is not verified
     * @param verifierLoader the class loader value (possibly
     *        <code>null</code>) to pass to {@link
     *        net.jini.security.Security#verifyCodebaseIntegrity
     *        Security.verifyCodebaseIntegrity}, if
     *        <code>verifyCodebaseIntegrity</code> is <code>true</code>
     * @param context the collection of context information objects or
     *        <code>null</code>
     * @return a new copy of the contained object
     * @throws IOException if an 
     *         <code>IOException</code> occurs while deserializing the
     *         object from its internal representation
     * @throws ClassNotFoundException if any classes necessary
     *         for reconstructing the contained object can not
     *         be found or if <code>verifyCodebaseIntegrity</code>
     *         is <code>true</code> and the integrity of the
     *         contained object's codebase cannot be confirmed
     */
    public T get(ClassLoader defaultLoader,
		      final boolean verifyCodebaseIntegrity,
		      ClassLoader verifierLoader,
		      Collection context)
	throws IOException, ClassNotFoundException 
    {
	if (objBytes == null)   // must have been a null object
	    return null;
 
	if (context == null) {
	    context = Collections.singleton(
			new IntegrityEnforcement() {
			    public boolean integrityEnforced() {
				return verifyCodebaseIntegrity;
			    }
			} );
	}
	ByteArrayInputStream bin = new ByteArrayInputStream(objBytes);
	// locBytes is null if no annotations
	ByteArrayInputStream lin =
	    (locBytes == null ? null : new ByteArrayInputStream(locBytes));
	MarshalledInstanceInputStream in =
	    new MarshalledInstanceInputStream(bin, lin,
					      defaultLoader,
					      verifyCodebaseIntegrity,
					      verifierLoader,
					      context);
	in.useCodebaseAnnotations();
        @SuppressWarnings("unchecked")
	T obj = (T) in.readObject();
	in.close();
	return obj;
    }

    /**
     * Compares this <code>MarshalledInstance</code> to another
     * object. Returns true if and only if the argument refers to an instance
     * of <code>MarshalledInstance</code> that contains exactly the same
     * serialized form for its contained object as this object does and
     * has the same class codebase annotations.
     *
     * @param obj the object to compare with this
     *            <code>MarshalledInstance</code>
     * @return <code>true</code> if the argument contains an object
     *         with an equivalent serialized form and codebase;
     *	       otherwise returns <code>false</code>
     */
    public boolean fullyEquals(Object obj) {
	if (equals(obj)) {
	    MarshalledInstance other = (MarshalledInstance)obj;
	    return Arrays.equals(locBytes, other.locBytes);
	}
	return false;
    }

    /**
     * Compares this <code>MarshalledInstance</code> to another
     * object. Returns true if and only if the argument refers to an instance
     * of <code>MarshalledInstance</code> that contains exactly the same
     * serialized form for its contained object as this object does. The
     * comparison ignores any class codebase annotations, so that
     * two objects can be equivalent if they have the same serialized
     * representation, except for the codebase of each class in the
     * serialized representation.
     * @param obj the object to compare with this
     *            <code>MarshalledInstance</code>
     * @return <code>true</code> if the argument contains an object
     *         with an equivalent serialized form; otherwise returns
     *         <code>false</code>
     */
    public boolean equals(Object obj) {
	if (obj == this)
	    return true;

	if (obj instanceof MarshalledInstance) {
	    MarshalledInstance other = (MarshalledInstance)obj;
	    if (hash != other.hash)
		return false;
	    return Arrays.equals(objBytes, other.objBytes);
	}
	return false;
    }

    /**
     * Returns the hash code for this <code>MarshalledInstance</code>.
     * The hash code is calculated only from the serialized form
     * of the contained object.
     * @return The hash code for this object
     */
    public int hashCode() {
	return hash;
    }

    /**
     * Verify the case of null contained object.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();

	// If contained object is null, then hash and locBytes must be
	// proper
	//
	if ((objBytes == null) && ((hash != 13) || (locBytes != null)))
	    throw new InvalidObjectException("Bad hash or annotation");
    }

    /**
     * Protect against missing superclass.
     */
    private void readObjectNoData() throws ObjectStreamException {
	throw new InvalidObjectException("Bad class hierarchy");
    } 
    
    private static class MarshalledInstanceInputStream extends MarshalInputStream {

        private ObjectInputStream locIn;

        MarshalledInstanceInputStream(InputStream objIn, InputStream locIn, ClassLoader defaultLoader, boolean verifyCodebaseIntegrity, ClassLoader verifierLoader, Collection context) throws IOException {
            super(objIn, defaultLoader, verifyCodebaseIntegrity, verifierLoader, context);
            this.locIn = (locIn == null ? null : new ObjectInputStream(locIn));
        }

        @Override
        protected String readAnnotation() throws IOException, ClassNotFoundException {
            return locIn == null ? null : (String) locIn.readObject();
        }
    }
    
    private static class MarshalledInstanceOutputStream extends MarshalOutputStream {

        private ObjectOutputStream locOut;
        /** <code>true</code> if non-<code>null</code> annotations are
         *  written.
         */
        private boolean hadAnnotations;

        public MarshalledInstanceOutputStream(OutputStream objOut, OutputStream locOut, Collection context) throws IOException {
            super(objOut, context);
            this.locOut = new ObjectOutputStream(locOut);
            hadAnnotations = false;
        }

        /**
         * Returns <code>true</code> if any non-<code>null</code> location
         * annotations have been written to this stream.
         */
        public boolean hadAnnotations() {
            return hadAnnotations;
        }

        @Override
        protected void writeAnnotation(String loc) throws IOException {
            hadAnnotations |= (loc != null);
            locOut.writeObject(loc);
        }

        @Override
        public void flush() throws IOException {
            super.flush();
            locOut.flush();
        }
    }
}
