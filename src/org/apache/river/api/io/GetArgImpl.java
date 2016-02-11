/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.river.api.io;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import net.jini.io.ObjectStreamContext;

/**
 *
 * @author peter
 */
class GetArgImpl extends AtomicSerial.GetArg {
    private static final ClassContextAccess context 
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
	return classFields.get(context.caller()).getObjectStreamClass();
    }

    @Override
    public boolean defaulted(String name) throws IOException {
	return classFields.get(context.caller()).defaulted(name);
    }

    @Override
    public boolean get(String name, boolean val) throws IOException {
	return classFields.get(context.caller()).get(name, val);
    }

    @Override
    public byte get(String name, byte val) throws IOException {
	return classFields.get(context.caller()).get(name, val);
    }

    @Override
    public char get(String name, char val) throws IOException {
	return classFields.get(context.caller()).get(name, val);
    }

    @Override
    public short get(String name, short val) throws IOException {
	return classFields.get(context.caller()).get(name, val);
    }

    @Override
    public int get(String name, int val) throws IOException {
	return classFields.get(context.caller()).get(name, val);
    }

    @Override
    public long get(String name, long val) throws IOException {
	return classFields.get(context.caller()).get(name, val);
    }

    @Override
    public float get(String name, float val) throws IOException {
	return classFields.get(context.caller()).get(name, val);
    }

    @Override
    public double get(String name, double val) throws IOException {
	return classFields.get(context.caller()).get(name, val);
    }

    @Override
    public Object get(String name, Object val) throws IOException {
	return classFields.get(context.caller()).get(name, val);
    }

    @Override
    public <T> T get(String name, T val, Class<T> type) throws IOException {
	// T will be replaced by Object by the compilers erasure.
	T result = (T) classFields.get(context.caller()).get(name, val);
	if (type.isInstance(result)) {
	    return result;
	}
	if (result == null) {
	    return null;
	}
	InvalidObjectException e = new InvalidObjectException("Input validation failed");
	e.initCause(new ClassCastException("Attempt to assign object of incompatible type"));
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
	//	    Class c = context.caller();
	//	    System.out.println("CALLER: " + c);
	//	    System.out.println(readers);
	return readers.get(context.caller());
    }
    
    /**
     * Dummy security manager providing access to getClassContext method.
     */
    private static class ClassContextAccess extends SecurityManager {
	/**
	 * Returns caller's caller class.
	 */
	Class caller() {
	    return getClassContext()[2];
	}
    }
    
    
    
}
