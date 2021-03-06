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
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.net.URL;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;
import net.jini.io.ObjectStreamContext;

/**
 *
 * @author peter
 */
class GetArgImpl extends AtomicSerial.GetArg {
    private static final ClassContextAccess CONTEXT 
	= AccessController.doPrivileged(
	    new PrivilegedAction<ClassContextAccess>(){

		@Override
		public ClassContextAccess run() {
		    return new ClassContextAccess();
		}

	    });
    
    final Map<Class, ObjectInputStream.GetField> classFields;
    final Map<Class, AtomicSerial.ReadObject> readers;
    final ObjectInput in;

    GetArgImpl(Map<Class, ObjectInputStream.GetField> args, Map<Class, AtomicSerial.ReadObject> readers, ObjectInput in) {
	super(false); // Avoids permission check.
	classFields = args;
	this.readers = readers;
	this.in = in;
    }

    @Override
    public ObjectStreamClass getObjectStreamClass() {
	return classFields.get(CONTEXT.caller()).getObjectStreamClass();
    }

    @Override
    public boolean defaulted(String name) throws IOException {
	return classFields.get(CONTEXT.caller()).defaulted(name);
    }

    @Override
    public boolean get(String name, boolean val) throws IOException {
	ObjectInputStream.GetField fields = classFields.get(CONTEXT.caller());
	return fields != null ? fields.get(name, val) : val;
    }

    @Override
    public byte get(String name, byte val) throws IOException {
	ObjectInputStream.GetField fields = classFields.get(CONTEXT.caller());
	return fields != null ? fields.get(name, val) : val;
    }

    @Override
    public char get(String name, char val) throws IOException {
	ObjectInputStream.GetField fields = classFields.get(CONTEXT.caller());
	return fields != null ? fields.get(name, val) : val;
    }

    @Override
    public short get(String name, short val) throws IOException {
	ObjectInputStream.GetField fields = classFields.get(CONTEXT.caller());
	return fields != null ? fields.get(name, val) : val;
    }

    @Override
    public int get(String name, int val) throws IOException {
	ObjectInputStream.GetField fields = classFields.get(CONTEXT.caller());
	return fields != null ? fields.get(name, val) : val;
    }

    @Override
    public long get(String name, long val) throws IOException {
	ObjectInputStream.GetField fields = classFields.get(CONTEXT.caller());
	return fields != null ? fields.get(name, val) : val;
    }

    @Override
    public float get(String name, float val) throws IOException {
	ObjectInputStream.GetField fields = classFields.get(CONTEXT.caller());
	return fields != null ? fields.get(name, val) : val;
    }

    @Override
    public double get(String name, double val) throws IOException {
	ObjectInputStream.GetField fields = classFields.get(CONTEXT.caller());
	return fields != null ? fields.get(name, val) : val;
    }

    @Override
    public Object get(String name, Object val) throws IOException {
	ObjectInputStream.GetField fields = classFields.get(CONTEXT.caller());
	return fields != null ? fields.get(name, val) : val;
    }

    @Override
    public <T> T get(String name, T val, Class<T> type) throws IOException, ClassNotFoundException {
	// T will be replaced by Object by the compilers erasure.
	ObjectInputStream.GetField fields = classFields.get(CONTEXT.caller());
	T result = null;
	if (fields instanceof EmulatedFieldsForLoading){
	    result = ((EmulatedFieldsForLoading) fields).get(name, val, type);
	} else if (fields != null){
	    result = (T) fields.get(name, val);
	}	
	if (type.isInstance(result)) {
	    return result;
	}
	if (result == null) {
	    return val;
	}
	InvalidObjectException e = new InvalidObjectException("Input validation failed");
	e.initCause(new ClassCastException("Attempt to assign object of incompatible type: "
		+ result.getClass().toString() + "\n"
		+ AccessController.doPrivileged(new Codebase(result.getClass()))
		+ "Should be and instance of: " + type.toString() + "\n"
		+ AccessController.doPrivileged(new Codebase(type))
	));
	throw e;
    }

    @Override
    public Collection getObjectStreamContext() {
	if (in instanceof ObjectStreamContext) {
	    return ((ObjectStreamContext) in).getObjectStreamContext();
	}
	return Collections.emptyList();
    }

    @Override
    public Class[] serialClasses() {
	return classFields.keySet().toArray(new Class[classFields.size()]);
    }

    @Override
    public AtomicSerial.ReadObject getReader() {
	//TODO capture any Exceptions and rethrow here.
	//	    Class c = CONTEXT.caller();
	//	    System.out.println("CALLER: " + c);
	//	    System.out.println(readers);
	return readers.get(CONTEXT.caller());
    }

    /**
     * Simple method to validate an object's parameters, this has been provided
     * for subclasses, that have simple invariant checks and don't contain intra class invariants between child
     * class and super class, but where invariants must be checked prior to
     * Object's default constructor being called.
     * 
     * @param fields an array containing field names.
     * @param types an array containing the type of each field.
     * @param nonNull a boolean array containing true if a field must not be null. 
     * @return
     * @throws IOException if invariants aren't satisfied.
     * @throws NullPointerException if any arguments are null.
     * @throws IllegalArgumentException if array lengths are not equal.
     */
    @Override
    public AtomicSerial.GetArg validateInvariants(String[] fields, Class[] types, boolean[] nonNull) throws IOException {
	Class caller = CONTEXT.caller();
	if (fields == null || types == null || nonNull == null) 
	    throw new NullPointerException("null arguments not allowed");
	if (fields.length != types.length || fields.length != nonNull.length) 
	    throw new IllegalArgumentException("array arguments must have equal lengths");
	for(int i = 0, l = fields.length; i < l; i++){
	    if (types[i].isPrimitive()){
		if (types[i] == byte.class) classFields.get(caller).get(fields[i],(byte)0);
		if (types[i] == char.class) classFields.get(caller).get(fields[i],(char)0);
		if (types[i] == short.class) classFields.get(caller).get(fields[i],(short)0);
		if (types[i] == int.class) classFields.get(caller).get(fields[i], 0);
		if (types[i] == long.class) classFields.get(caller).get(fields[i], 0L);
		if (types[i] == double.class) classFields.get(caller).get(fields[i],(double)0);
		if (types[i] == float.class) classFields.get(caller).get(fields[i], 0.0F);
	    } else {
		Object o = classFields.get(caller).get(fields[i], null);
		if (nonNull[i] && o == null) {
		    throw new InvalidObjectException(fields[i] 
			    + " cannot be null");
		} else if (!types[i].isInstance(o)){
		    throw new InvalidObjectException(fields[i] 
			    + " must be an instance of " + types[i]);
		}
	    }
	}
	return this;
    }
    
    /**
     * Dummy security manager providing access to getClassContext method.
     */
    private static class ClassContextAccess extends SecurityManager {
	/**
	 * Returns caller's caller class.
	 */
	Class caller() {
	    Class caller = getClassContext()[2];
	    return caller;
	}
    }
    
    private static class Codebase implements PrivilegedAction<String> {
	
	private final Class claz;
	
	Codebase(Class claz){
	    this.claz = claz;
	}

	public String run() {
	    StringBuilder sb = new StringBuilder(128);
	    CodeSource cs = claz.getProtectionDomain().getCodeSource();
	    sb.append("CodeSource: ");
	    sb.append(cs == null ? "null" : cs.toString());
	    sb.append("\n");
	    ClassLoader cl = claz.getClassLoader();
	    while (cl != null) {
		sb.append("ClassLoader: ");
		sb.append(cl.toString());
		cl = cl.getParent();
		sb.append(", Parent ");
	    }
	    sb.append("ClassLoader: Java system boot loader");
	    sb.append("\n");
	    return sb.toString();
	}
	
    }
    
}
