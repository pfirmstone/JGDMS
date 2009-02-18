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
package com.sun.jini.test.share.reggie;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.rmi.server.RMIClassLoader;
import java.security.DigestOutputStream;
import java.security.MessageDigest;

/**
 * An EntryClass is a descriptor for an entry class, packaged up for
 * transmission between client-side proxies and the registrar server.
 * Instances are never visible to clients, they are private to the
 * communication between the proxies and the server.  Note that we don't
 * transmit information about interfaces implemented by the class, because it
 * isn't necessary given the specific use of type information for entries.
 * <p>
 * This class only has a bare minimum of methods, to minimize
 * the amount of code downloaded into clients.
 * <p>
 * Equality is based on object identity, on the assumption that
 * ClassResolver is always used and that equality is only interesting
 * in the registrar.
 *
 * @author Sun Microsystems, Inc.
 *
 * @see ClassMapper
 * @see ClassResolver
 */
class EntryClass implements java.io.Serializable {

    /**
     * Class name
     *
     * @serial
     */
    protected String name;
    /**
     * Descriptor for the superclass
     *
     * @serial
     */
    protected EntryClass superclass;
    /**
     * Number of public fields
     *
     * @serial
     */
    protected int numFields;
    /**
     * Hash of the public fields
     *
     * @serial
     */
    protected long hash;

    /** Number of instances of this class in service registrations */
    protected transient int numInstances;
    /** Number of templates of this class in event registrations */
    protected transient int numTemplates;
    /**
     * An instance containing only name, no superclass info.
     * This is only used on the registrar side, to minimize the amount
     * of info transmitted back to clients.
     */
    protected transient EntryClass replacement;

    private static final long serialVersionUID = 529192974214549379L;

    /**
     * Should only be called by ClassMapper. A SHA-1 message digest is
     * computed on a stream constructed with: the hash of the superclass
     * written as a long (if there is a superclass), then for each of the
     * fields declared by this class (that is, excluding fields from
     * superclasses) in alphabetic order, the field name written as UTF
     * followed by the field type name written as UTF. The first 8 bytes of
     * the digest are used to form a 64-bit hash value.
     */
    public EntryClass(Class clazz, EntryClass superclass) {
	name = clazz.getName();
	this.superclass = superclass;
	ClassMapper.EntryField[] fields = ClassMapper.getFields(clazz);
	numFields = fields.length;
	if (superclass != null && numFields == superclass.numFields) {
	    hash = superclass.hash;
	} else if (numFields != 0) {
	    try {
		MessageDigest md = MessageDigest.getInstance("SHA");
		DataOutputStream out =
		    new DataOutputStream(
			new DigestOutputStream(new ByteArrayOutputStream(127),
					       md));
		if (superclass != null)
		    out.writeLong(superclass.hash);
		for (int i = superclass.numFields; i < fields.length; i++) {
		    out.writeUTF(fields[i].field.getName());
		    out.writeUTF(fields[i].field.getType().getName());
		}
		out.flush();
		byte[] digest = md.digest();
		for (int i = Math.min(8, digest.length); --i >= 0; ) {
		    hash += ((long) (digest[i] & 0xFF)) << (i * 8);
		}
	    } catch (Exception e) {
		RegistrarProxy.handleException(e);
		hash = 0;
	    }
	}
    }

    /**
     * Constructor used for creating replacement instances,
     * containing only name.
     */
    private EntryClass(EntryClass orig) {
	name = orig.name;
    }

    /** Return the class name */
    public String getName() {
	return name;
    }

    /** Return the superclass descriptor */
    public EntryClass getSuperclass() {
	return superclass;
    }

    /** Return the number of public fields (including superclasses) */
    public int getNumFields() {
	return numFields;
    }

    /** Return the hash of the public fields */
    public long getFieldsHash() {
	return hash;
    }

    /** Set the hash of the public fields */
    public void setFieldsHash(long hash) {
	this.hash = hash;
    }

    /** Return the number of instances of this class */
    public int getNumInstances() {
	return numInstances;
    }

    /** Set the number of instances of this class */
    public void setNumInstances(int numInstances) {
	this.numInstances = numInstances;
    }

    /** Return the number of templates of this class */
    public int getNumTemplates() {
	return numTemplates;
    }

    /** Set the number of templates of this class */
    public void setNumTemplates(int numTemplates) {
	this.numTemplates = numTemplates;
    }

    /** Return the replacement, if any, containing only name and rep. */
    public EntryClass getReplacement() {
	return replacement;
    }

    /**
     * This is really only needed in the registrar, but it's very
     * convenient to have here.
     * @see Class#isAssignableFrom
     */
    public boolean isAssignableFrom(EntryClass cls) {
	for (EntryClass sup = cls; sup != null; sup = sup.superclass) {
	    if (this == sup)
		return true;
	}
	return false;
    }

    /** Converts the descriptor to a Class instance, loading from codebase */
    public Class toClass(String codebase)
	throws IOException, ClassNotFoundException
    {
	return RMIClassLoader.loadClass(codebase, name);
    }

    /**
     * Sets this descriptor to be the canonical one.  Replaces the
     * superclass with the canonical superclass, and constructs the
     * replacement object.
     * <p>
     * This should only be called by ClassResolver.
     */
    public void canonical(EntryClass superclass) {
	this.superclass = superclass;
	replacement = new EntryClass(this);
    }
}
