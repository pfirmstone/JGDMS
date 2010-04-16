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
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import net.jini.io.context.IntegrityEnforcement;

/**
 * This is an experimental implementation and may be subject to change or
 * removal.
 * 
 * This will be removed shortly, a CDC version of MarshalledInstance will be
 * compiled withouth the deprecated methods.  PackageVersion will be changed
 * to be an package version annotation generator.
 * 
 * A <code>CDCMarshalledObject</code> contains an object in serialized
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
 * If the <code>CDCMarshalledObject</code> needs to deserialize the
 * contained object then the contained object will be deserialized with the
 * deserialization semantics defined by <code>MarshalInputStream</code>.
 * In particular, the codebase annotations associated with the contained
 * object may be used to load classes referenced by the contained object.
 * <p>
 * <code>CDCMarshalledObject</code> provides functionality similar to
 * <code>java.rmi.MarshalledObject</code>, but additionally provides
 * for the verification of codebase integrity. Unlike
 * <code>java.rmi.MarshalledObject</code>, it does not perform remote
 * object-to-stub replacement.
 * <p>
 * <code>CDCMarshalledObject</code> differes from <code>MarshalledInstance</code>
 * in lacking a dependency upon java.rmi.MarshalledObject as the CDC Personal Profile
 * lacks the rmi server implementation. <code>Convert</code> provides methods
 * for conversion between the various implementations.
 * </p>
 * 
 *
 * @param T - Type of Object
 * @author Peter Firmstone.
 * @since 2.2.0
 */
public class CDCMarshalledObject<T> implements Serializable {
    
    /**
     * @serial Bytes of serialized representation.  If <code>objBytes</code> is
     * <code>null</code> then the object marshalled was a <code>null</code>
     * reference.
     */  
    private byte[] objBytes;
 
    /**
     * @serial Bytes of location annotations, which are ignored by
     * <code>equals</code>.  If <code>locBytes</code> is null, there were no
     * non-<code>null</code> annotations during marshalling.
     */  
    private byte[] locBytes;
 
    /**
     * @serial Stored hash code of contained object.
     *   
     * @see #hashCode
     */  
    private final int hash;
    
    private final PackageVersion packageVersion;
    
    static final long serialVersionUID = 1L;
    
    CDCMarshalledObject(MarshalledObject<T> mo, PackageVersion pv){
        // Don't worry about defensive copies this is package private
        objBytes = mo.objBytes;
        locBytes = mo.locBytes;
        hash = mo.hash;
        packageVersion = pv;
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
    public CDCMarshalledObject(T obj) throws IOException {
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
    public CDCMarshalledObject(T obj, Collection context)
	throws IOException
    {
	if (context == null) throw new NullPointerException();
	if (obj == null) {
	    hash = 13;	// null hash for java.rmi.MarshalledObject
            objBytes = null;
            locBytes = null;
            packageVersion = null;
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
        packageVersion = PackageVersion.getInstance(obj);
    }

    MarshalledObject<T> asMarshalledObject(){
        MarshalledObject<T> mo = new MarshalledObject<T>();
        // Don't worry about defensive copies, this is package private.
        mo.objBytes = objBytes;
        mo.locBytes = locBytes;
        mo.hash = hash;
        return mo;
    }
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof CDCMarshalledObject) {
            CDCMarshalledObject other = (CDCMarshalledObject) obj;
            if (hash != other.hash) {
                return false;
            }
            return Arrays.equals(objBytes, other.objBytes);
        }
        return false;
    }

    public boolean fullyEquals(Object obj) {
        if (equals(obj)) {
            CDCMarshalledObject other = (CDCMarshalledObject) obj;
            return Arrays.equals(locBytes, other.locBytes);
        }
        return false;
    }
    
    public PackageVersion getPackageVersion(){
        return packageVersion;
    }

    public T get(final boolean verifyCodebaseIntegrity) throws IOException, ClassNotFoundException {
        return get(null, verifyCodebaseIntegrity, null, null);
    }

    public T get(ClassLoader defaultLoader, final boolean verifyCodebaseIntegrity, ClassLoader verifierLoader, Collection context) throws IOException, ClassNotFoundException {
        if (objBytes == null) {
            // must have been a null object
            return null;
        }
        if (context == null) {
            context = Collections.singleton(new IntegrityEnforcement() {

                public boolean integrityEnforced() {
                    return verifyCodebaseIntegrity;
                }
            });
        }
        ByteArrayInputStream bin = new ByteArrayInputStream(objBytes);
        ByteArrayInputStream lin = locBytes == null ? null : new ByteArrayInputStream(locBytes);
        MarshalledInstanceInputStream in = new MarshalledInstanceInputStream(bin, lin, defaultLoader, verifyCodebaseIntegrity, verifierLoader, context);
        in.useCodebaseAnnotations();
        @SuppressWarnings("unchecked")
        T obj = (T) in.readObject();
        in.close();
        return obj;
    }

    /**
     * Returns the hash code for this <code>MarshalledInstance</code>.
     * The hash code is calculated only from the serialized form
     * of the contained object.
     * @return The hash code for this object
     */
    @Override
    public int hashCode() {
        return hash;
    }
     
    private void writeObject(ObjectOutputStream out) throws IOException{
        out.defaultWriteObject();
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
        // Defensive copy of arrays to prevent unmarshalling attack using
        // stolen reference for mutable fields.  array.clone() not working? jdk1.6
        objBytes = Arrays.copyOf(objBytes, objBytes.length);
        locBytes = Arrays.copyOf(locBytes, locBytes.length);
    }

    /**
     * Protect against missing superclass.
     */
    private void readObjectNoData() throws ObjectStreamException {
	throw new InvalidObjectException("Bad class hierarchy");
    }        
}
