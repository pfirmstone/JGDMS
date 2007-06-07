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
package com.sun.jini.reggie;

import com.sun.jini.proxy.MarshalledWrapper;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.rmi.MarshalException;
import java.rmi.UnmarshalException;
import java.util.StringTokenizer;
import java.security.DigestOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;
import net.jini.loader.ClassLoading;

/**
 * A ServiceType is a descriptor for a class, packaged up for
 * transmission between client-side proxies and the registrar server.
 * Instances are never visible to clients, they are private to the
 * communication between the proxies and the server.
 * <p>
 * This class only has a bare minimum of methods, to minimize
 * the amount of code downloaded into clients.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see ClassMapper
 * @see ClassResolver
 */
class ServiceType implements Serializable {

    private static final long serialVersionUID = 2L;
    private static final ServiceType[] empty = {};

    /**
     * Class name. If the class is generated by java.lang.reflect.Proxy,
     * then the name is of the form ";iface1;iface2;...;ifaceN".
     *
     * @serial
     */
    private String name;
    /**
     * Hash for the type
     *
     * @serial
     */
    protected long hash;
    /**
     * Descriptor for the superclass.
     *
     * @serial
     */
    protected ServiceType superclass;
    /**
     * Descriptor for the interfaces.  As a special case, interfaces is
     * null for the descriptor for java.lang.Object, and non-null otherwise.
     * This avoids carrying a boolean isInterface around.
     *
     * @serial
     */
    protected ServiceType[] interfaces;
    /**
     * An instance containing only name, no supertype info.
     * This is only used on the registrar side, to minimize the amount
     * of info transmitted back to clients.
     */
    protected transient ServiceType replacement;
    /** 
     * Flag set to true if this instance was unmarshalled from an
     * integrity-protected stream, or false otherwise
     */
    private transient boolean integrity = false;

    /** Should only be called by ClassMapper */
    public ServiceType(Class clazz,
		       ServiceType superclass,
		       ServiceType[] interfaces)
	throws MarshalException
    {
	if (!Proxy.isProxyClass(clazz)) {
	    name = clazz.getName();
	} else if (interfaces.length == 0) {
	    name = ";";
	} else {
	    StringBuffer buf = new StringBuffer();
	    for (int i = 0; i < interfaces.length; i++) {
		buf.append(';');
		buf.append(interfaces[i].getName());
	    }
	    name = buf.toString();
	}
	this.superclass = superclass;
	if (clazz != Object.class)
	    this.interfaces = interfaces;
	try {
	    computeHash();
	} catch (Exception e) {
	    throw new MarshalException("unable to calculate the type hash for "
				       + name, e);
	}
    }

    /**
     * Constructor used for creating replacement instances,
     * containing only name.
     */
    private ServiceType(ServiceType stype) {
	name = stype.name;
    }

    /**
     * Returns the name of this type
     * @return the name of this type
     */
    public String getName() {
	return name;
    }

    /** Return the superclass descriptor */
    public ServiceType getSuperclass() {
	return superclass;
    }

    /** Return the interfaces.  The array is not a copy; do not modify it. */
    public ServiceType[] getInterfaces() {
	if (interfaces != null)
	    return interfaces;
	return empty;
    }

    /** Return the replacement, if any, containing only name and rep. */
    public synchronized ServiceType getReplacement() {
	if (replacement == null)
	    replacement = new ServiceType(this);
	return replacement;
    }

    /** 
     * Test if this isAssignableFrom any of the given interface types. 
     * Note ifaces cannot be null.
     */
    private boolean isAssignableFrom(ServiceType[] ifaces)
    {
	for (int i = ifaces.length; --i >= 0; ) {
	    if (hash == ifaces[i].hash ||
		isAssignableFrom(ifaces[i].interfaces))
		return true;
	}
	return false;
    }

    /** @see Class#isInterface */
    public boolean isInterface() {
	return (superclass == null && interfaces != null);
    }

   /**
    * Returns true if this type is equal to <code>type</code> or if this type
    * is equal to a superclass of <code>type</code>.
    *
    * @param type Type to check if subclass of this class
    * @return true if <code>type</code> is a subclass of this type, false
    * otherwise
    * @see java.lang.Class#isAssignableFrom
     */
    public boolean isAssignableFrom(ServiceType cls) {
       if (hash == cls.hash)
	    return true;
	if (isInterface()) {
	    if (cls.interfaces != null && isAssignableFrom(cls.interfaces))
		return true;
	    for (ServiceType sup = cls.superclass;
		 sup != null && sup.interfaces != null;
		 sup = sup.superclass)
	    {
		if (isAssignableFrom(sup.interfaces))
		    return true;
	    }
	} else {
	    for (ServiceType sup = cls.superclass;
		 sup != null;
		 sup = sup.superclass)
	    {
	       if (hash == sup.hash)
		    return true;
	    }
	}
	return false;
    }

    /**
     * Converts this descriptor to a Class instance, loading from codebase
     *
     * @param codebase String the codebase to load the class from
     * @return Class the class this descriptor represents
     */
    public Class toClass(String codebase)
	throws IOException, ClassNotFoundException
    {
	if (name.charAt(0) != ';') {
	    return ClassLoading.loadClass(
		codebase, name, null, integrity, null);
	}
	StringTokenizer st = new StringTokenizer(name, ";");
	String[] ifs = new String[st.countTokens()];
	for (int i = 0; i < ifs.length; i++) {
	    ifs[i] = st.nextToken();
	}
	return ClassLoading.loadProxyClass(
	    codebase, ifs, null, integrity, null);
    }

    /**
     * Returns true if the object passed in is an instance of Type
     * with the same type hash.  Returns false otherwise.
     * @param o object to compare this object against
     * @return true if this object equals the object passed in; false
     * otherwise.
     */
    public boolean equals(Object o) {
	if (this == o) return true;
	if (!(o instanceof ServiceType))
	    return false;
	ServiceType t = (ServiceType) o;
	return hash == t.hash;
    }

    /**
     * Return a hashcode for this type.
     * @return int the hashcode for this type
     */
    public int hashCode() {
	return (int) (hash ^ (hash >>> 32));
    }

    /* Inherit javadoc */
    public String toString() {
	return getClass() + "[name=" + getName() + "]";
    }
    
    /**
     * Computes a SHA-1 digest from the hash of the superclass, if there
     * is a superclass, followed by the name of this class, followed by
     * the name and type for each field, if any, declared by this class and
     * ordered alphabetically by field name.  The first 8 bytes of the digest
     * are used to form the 64-bit hash value for this type.
     */
    private void computeHash() throws IOException, NoSuchAlgorithmException
    {
	hash = 0;
	MessageDigest md = MessageDigest.getInstance("SHA");
	DataOutputStream out = new DataOutputStream(
	    new DigestOutputStream(new ByteArrayOutputStream(127),md));	    
	out.writeUTF(name);	   
	out.flush();
	byte[] digest = md.digest();
	for (int i = Math.min(8, digest.length); --i >= 0; ) {
	    hash += ((long) (digest[i] & 0xFF)) << (i * 8);
	}
    }

    /**
     * Samples integrity protection setting (if any) of the stream from which
     * this instance is being deserialized.
     */
    private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
    {
	in.defaultReadObject();
	if (name == null)
	    throw new InvalidObjectException("name cannot be null");
	integrity = MarshalledWrapper.integrityEnforced(in);
	if (hash == 0) {
	    try {
		computeHash();
	    } catch (Exception e) {
		throw new UnmarshalException("unable to calculate the type"
					     + " hash for " + name, e);
	    }
	}
    }

    /**
     * Throws InvalidObjectException, since data for this class is required.
     */
    private void readObjectNoData() throws InvalidObjectException {
	throw new InvalidObjectException("no data");
    }

}

