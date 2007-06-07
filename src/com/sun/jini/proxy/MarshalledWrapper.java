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
package com.sun.jini.proxy;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import net.jini.io.MarshalledInstance;
import net.jini.io.ObjectStreamContext;
import net.jini.io.context.IntegrityEnforcement;

/**
 * Wrapper around {@link MarshalledInstance} that samples the integrity setting
 * (if any) of the stream it is unmarshalled from, and uses that setting to
 * determine whether or not to verify codebase integrity when calling the
 * {@link MarshalledInstance#get(boolean) get} method of the contained
 * <code>MarshalledInstance</code>.
 *
 * @author Sun Microsystems, Inc.
 *
 * @since 2.0
 */
public class MarshalledWrapper implements Serializable {

    private static final long serialVersionUID = 2L;

    /** 
     * The wrapped MarshalledInstance.
     *
     * @serial
     */
    private final MarshalledInstance instance;
    /** 
     * Flag set to true if this instance was unmarshalled from an
     * integrity-protected stream, or false otherwise
     */
    private transient boolean integrity = false;

    /**
     * Returns the integrity protection setting of the given stream, determined
     * as follows: if the stream implements {@link ObjectStreamContext} and has
     * in its context collection an object of type
     * {@link IntegrityEnforcement}, the
     * {@link IntegrityEnforcement#integrityEnforced integrityEnforced} method
     * of that object is called and the resulting value returned; otherwise,
     * <code>false</code> is returned.
     *
     * @return integrity protection setting of the given stream
     */
    public static boolean integrityEnforced(ObjectInputStream stream) {
	if (stream instanceof ObjectStreamContext) {
	    Collection ctx =
		((ObjectStreamContext) stream).getObjectStreamContext();
	    for (Iterator i = ctx.iterator(); i.hasNext(); ) {
		Object obj = i.next();
		if (obj instanceof IntegrityEnforcement) {
		    return ((IntegrityEnforcement) obj).integrityEnforced();
		}
	    }
	}
	return false;
    }

    /**
     * Creates a new <code>MarshalledWrapper</code> wrapping a
     * <code>MarshalledInstance</code> containing the given object.
     *
     * @param obj object to create <code>MarshalledInstance</code> with
     * @throws IOException if <code>MarshalledInstance</code> creation fails
     */
    public MarshalledWrapper(Object obj) throws IOException {
	instance = new MarshalledInstance(obj);
    }

    /**
     * Creates a new <code>MarshalledWrapper</code> wrapping the given
     * <code>MarshalledInstance</code>.
     *
     * @param instance <code>MarshalledInstance</code> to wrap
     * @throws NullPointerException if <code>instance</code> is
     * <code>null</code>
     */
    public MarshalledWrapper(MarshalledInstance instance) {
	if (instance == null) {
	    throw new NullPointerException();
	}
	this.instance = instance;
    }

    /**
     * Returns the result of calling the {@link MarshalledInstance#get(boolean)
     * get} method of the wrapped <code>MarshalledInstance</code>, passing the
     * integrity value sampled during deserialization as the
     * <code>verifyCodebaseIntegrity</code> argument.  If this
     * <code>MarshalledWrapper</code> instance was not produced by
     * deserialization or was deserialized from a stream with no integrity
     * protection setting, then a <code>verifyCodebaseIntegrity</code> value of
     * <code>false</code> is used.
     *
     * @return the object unmarshalled by the wrapped
     * <code>MarshalledInstance</code>
     * @throws IOException if an <code>IOException</code> occurs during
     * unmarshalling
     * @throws ClassNotFoundException if any classes necessary for
     * reconstructing the object being unmarshalled cannot be found
     */
    public Object get() throws IOException, ClassNotFoundException {
	return instance.get(integrity);
    }

    /**
     * Returns the <code>MarshalledInstance</code> wrapped by this
     * <code>MarshalledWrapper</code>.
     *
     * @return wrapped <code>MarshalledInstance</code>
     */
    public MarshalledInstance getMarshalledInstance() {
	return instance;
    }

    /**
     * Returns the integrity value sampled during deserialization of this
     * <code>MarshalledWrapper</code> instance, or <code>false</code> if this
     * instance was not produced by deserialization or was deserialized from a
     * stream with no integrity protection setting.
     *
     * @return sampled integrity value
     */
    public boolean getIntegrity() {
	return integrity;
    }

    /**
     * Returns the hash code value for this <code>MarshalledWrapper</code>.
     *
     * @return the hash code value for this <code>MarshalledWrapper</code>.
     */
    public int hashCode() {
	return MarshalledWrapper.class.hashCode() ^ instance.hashCode();
    }

    /**
     * Compares the specified object with this <code>MarshalledWrapper</code>
     * for equality.  This method returns <code>true</code> if and only if the
     * specified object is of the same class as this object and its wrapped
     * <code>MarshalledInstance</code> is equal to the one in this object.
     *
     * @param obj object to compare with
     * @return <code>true</code> if <code>obj</code> is equivalent to
     * this object; <code>false</code> otherwise
     */
    public boolean equals(Object obj) {
	return obj == this ||
	       (obj != null &&
		obj.getClass() == getClass() &&
		instance.equals(((MarshalledWrapper) obj).instance));
    }

    /**
     * Samples integrity protection setting (if any) of the stream from which
     * this instance is being deserialized.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (instance == null) {
	    throw new InvalidObjectException("null instance");
	}
	integrity = integrityEnforced(in);
    }

    /**
     * Throws InvalidObjectException, since data for this class is required.
     */
    private void readObjectNoData() throws ObjectStreamException {
	throw new InvalidObjectException("no data");
    }
}
