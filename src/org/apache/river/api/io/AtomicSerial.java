/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.river.api.io;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.SerializablePermission;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.Guard;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import net.jini.io.ObjectStreamContext;

/**
 * Traditional java de-serialization cannot be used over untrusted connections
 * for the following reasons:
 * <p>
 * The serial stream can be manipulated to allow the attacker to instantiate
 * any Serializable object available on the CLASSPATH or any object that
 * has a default constructor, such as ClassLoader.
 * <p>
 * Failure to validate invariants during construction, or as a result of 
 * an exception, objects can remain in an invalid state after construction. 
 * During traditional de-serialization, an objects state is written after it's
 * creation, thus an attacker can steal a reference to the object without
 * any invariant check protection, by manipulating the stream.
 * <p>
 * In addition many java objects, including ObjectInputStream itself, read 
 * integer length values from the stream and instantiate arrays without checking 
 * the size first, so an attacker can easily cause an Error that brings
 * down the JVM. 
 * <p>
 * A requirement of implementing this interface is to implement a constructor
 * that accepts a single GetArg parameter.  This constructor may be
 * public or have default visibility, even in this case, the constructor
 * must be treated as a public constructor.
 * <p>
 * <code>
 * public AtomicSerialImpl(GetArg arg) throws InvalidObjectException{<br>
 *	super(check(arg)); // If super also implements @AtomicSerial<br>
 *	// Set fields here<br>
 * }<br>
 * </code>
 * In addition, before calling a superclass constructor, the class must
 * also implement a static invariant check method, for example:
 * <p>
 * static GetArg check(GetArg) throws InvalidObjectException;
 * <p>
 * Atomic stands for atomic failure, if invariants cannot be satisfied an 
 * instance cannot be created and hence a reference cannot be stolen.
 * <p>
 * The serial form of AtomicSerial is backward compatible with Serializable
 * classes that do not define a writeObject method.  It is also compatible 
 * with Serializable classes that define a writeObject method that calls
 * defaultWriteObject.  AtomicSerial provides backward compatibility with 
 * Serializable classes that implement writeObject and write other Objects
 * or primitives to the stream when {@link ReadObject} and {@link ReadInput}
 * are implemented by the class.
 * 
 * @author peter
 * @see ReadObject
 * @see ReadInput
 * @see GetArg
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AtomicSerial {
     
    
    /**
     * ReadObject that can be used to read stream data and Objects written
     * to the stream bywriteObject.
     * 
     * @see  ReadInput
     */
    public interface ReadObject {
	void read(ObjectInput input) throws IOException, ClassNotFoundException;
    }
    
    /**
     * Factory to test AtomicSerial instantiation compliance.
     */
    public static final class Factory {
	private Factory(){} // Non instantiable.
	
	/**
	 * Convenience method for testing implementing class constructor
	 * signature compliance.
	 * <p>
	 * De-serializers are free to implement higher performance instantiation
	 * that complies with this contract.
	 * <p>
	 * Only public and package default constructors can be called by 
	 * de-serializers.  Package default constructors have been provided
	 * to prevent implementations from polluting public api,
	 * but should be treated as public constructors.
	 * <p>
	 * Constructors with private visibility cannot be called.
	 * <p>
	 * Constructors with protected visibility can only be called by 
	 * subclasses, not de-serializers.
	 * 
	 * @param <T> AtomicSerial implementation type.
	 * @param type AtomicSerial implementing class.
	 * @param arg GetArg caller sensitive arguments used by implementing constructor.
	 * @return new instance of T.
	 * @throws java.io.InvalidClassException if constructor is non compliant
	 * or doesn't exist. 
	 * @throws java.io.InvalidObjectException if invariant check fails
	 * @throws NullPointerException if arg or type is null.
	 */
	public static <T> T instantiate(final Class<T> type, final GetArg arg)
		throws IOException {
	    if (arg == null) throw new NullPointerException();
	    if (type == null) throw new NullPointerException();
	    final Class[] param = { GetArg.class };
	    Object[] args = { arg };
	    Constructor<T> c;
	    try {
		c = AccessController.doPrivileged(
		    new PrivilegedExceptionAction<Constructor<T>>(){

			@Override
			public Constructor<T> run() throws Exception {
			    Constructor<T> c = type.getDeclaredConstructor(param);
			    int mods = c.getModifiers();
			    switch (mods){
				case Modifier.PUBLIC:
				    c.setAccessible(true); //In case constructor is public but class not.
				    return c;
				case Modifier.PROTECTED:
				    throw new InvalidClassException( type.getCanonicalName(),
					"protected constructor cannot be called by de-serializer");
				case Modifier.PRIVATE:
				    throw new InvalidClassException( type.getCanonicalName(),
					"private constructor cannot be called by de-serializer");
				default: // Package private
				    c.setAccessible(true);
				    return c;
			    }
			}

		    });
		return c.newInstance(args);
	    } catch (PrivilegedActionException ex) {
		Exception e = ex.getException();
		if (e instanceof NoSuchMethodException) throw new InvalidClassException(type.getCanonicalName(), "No matching AtomicSerial constructor signature found");
		if (e instanceof SecurityException ) throw (SecurityException) e;
		if (e instanceof InvalidClassException ) throw (InvalidClassException) e;
		InvalidClassException ice = new InvalidClassException("Unexpected exception while attempting to access constructor");
		ice.initCause(ex);
		throw ice;
	    } catch (InvocationTargetException ex) {
		Throwable e = ex.getCause();
		if (e instanceof InvalidObjectException) throw (InvalidObjectException) e;
		InvalidObjectException ioe = new InvalidObjectException(
		    "Validation of invariants failed during construction: " + type);
		ioe.initCause(ex);
		throw ioe;
	    } catch (IllegalAccessException ex) {
		throw new AssertionError("This shouldn't happen ", ex);
	    } catch (IllegalArgumentException ex) {
		throw new AssertionError("This shouldn't happen ", ex);
	    } catch (InstantiationException ex) {
		throw new InvalidClassException(type.getCanonicalName(), ex.getMessage());
	    }
	}
	
	public static ReadObject streamReader( final Class<?> streamClass) throws IOException {
	    if (streamClass == null) throw new NullPointerException();
	    try {
		Method readerMethod = AccessController.doPrivileged(
		    new PrivilegedExceptionAction<Method>(){
			@Override
			public Method run() throws Exception {
			    for (Method m : streamClass.getDeclaredMethods()){
				if (m.isAnnotationPresent(ReadInput.class)){
				    m.setAccessible(true);
				    return m;
				}
			    }
			    return null;
			}
		    }
		);
		if (readerMethod != null){
		    ReadObject result = (ReadObject) readerMethod.invoke(null, (Object []) null);
		    return result;
		}
	    } catch (PrivilegedActionException ex) {
		Exception e = ex.getException();
		if (e instanceof SecurityException ) throw (SecurityException) e;
		InvalidClassException ice = new InvalidClassException("Unexpected exception while attempting to obtain Reader");
		ice.initCause(ex);
		throw ice;
	    } catch (IllegalAccessException ex) {
		throw new AssertionError("This shouldn't happen ", ex);
	    } catch (IllegalArgumentException ex) {
		throw new AssertionError("This shouldn't happen ", ex);
	    } catch (InvocationTargetException ex) {
		InvalidClassException ice = new InvalidClassException("Unexpected exception while attempting to obtain Reader");
		ice.initCause(ex);
		throw ice;
	    }
	    return null;
	}
    }

    /**
     * If an object wishes to read from the stream during construction
     * it must provide a class static method with the following annotation.
     * <p>
     * The Serializer will use this static method to obtain a ReadObject instance
     * that will be invoked at the time of the streams choosing.
     * @see ReadObject
     */
    @Retention(value = RetentionPolicy.RUNTIME)
    @Target(value = ElementType.METHOD)
    public static @interface ReadInput {
    }

    /**
     * GetArg is the single argument to AtomicSerial's constructor
     * 
     * @author peter
     */
    public static abstract class GetArg extends ObjectInputStream.GetField 
					implements ObjectStreamContext {
	
	private static Method clone;
	private static Guard enableSubclassImplementation 
		= new SerializablePermission("enableSubclassImplementation");
	
	static {
	    try {
		clone = AccessController.doPrivileged(new PrivilegedExceptionAction<Method>(){
		    
		    @Override
		    public Method run() throws Exception {
			return Object.class.getDeclaredMethod("clone", new Class [0]);
		    }
		    
		});
	    } catch (PrivilegedActionException ex) {
		Exception cause = ex.getException();
		if (cause instanceof SecurityException) throw (SecurityException) cause;
		throw new Error(cause);
	    }
	}

	private static boolean check() {
	    enableSubclassImplementation.checkGuard(null);
	    return true;
	}
	
	/**
	 * Convenience method to create a shallow copy of an array
	 * if non null.
	 * 
	 * Since arrays are mutable, an attacker can retain a reference
	 * to a de-serialized array, that allows an attacker to mutate that
	 * array.
	 * 
	 * @param <T> type
	 * @param arry that will be cloned.
	 * @return A clone of arry, or null if arry is null.
	 */
	public static <T> T[] copy(T[] arry){
	    if (arry == null) return null;
	    return arry.clone();
	}
	
	/**
	 * Convenience method to perform a deep copy of an array containing
	 * Cloneable objects
	 * 
	 * @param <T>
	 * @param arry - may be null or contain null elements.
	 * @return A deep clone of arry, or null if arry is null.
	 * @throws CloneNotSupportedException 
	 */
	public static <T extends Cloneable> T[] deepCopy(T[] arry) throws CloneNotSupportedException{
	    if (arry == null) return null;
	    T[] cpy = copy(arry);
	    for (int i = 0, l = cpy.length; i < l; i++){
		cpy[i] = copy(cpy[i]);
	    }
	    return cpy;
	}
	
	/**
	 * Convenience method to copy Cloneable objects.
	 * 
	 * Instances of java.util.Collection will be replaced in the stream
	 * by a safe limited functionality immutable Collection instance 
	 * that must be passed to a collection instance constructor.
	 * 
	 * @param <T>
	 * @param obj
	 * @return a clone of obj if non null, otherwise null;
	 * @throws CloneNotSupportedException 
	 */
	public static <T extends Cloneable> T copy(T obj) throws CloneNotSupportedException{
	    if (obj == null) return null;
	    try {
		return (T) clone.invoke(obj, (Object) null);
	    } catch (IllegalAccessException ex) {
		throw thro(ex);
	    } catch (IllegalArgumentException ex) {
		throw thro(ex);
	    } catch (InvocationTargetException ex) {
		throw thro(ex);
	    }
	}
	
	private static CloneNotSupportedException thro(Throwable cause) {
	    CloneNotSupportedException ex = new CloneNotSupportedException("Clone unsuccessful");
	    ex.initCause(cause);
	    return ex;
	}
	
	/**
	 * Convenience method to check that an object is non null.
	 * @param <T>
	 * @param obj
	 * @param message reason for exception.
	 * @return obj if non null.
	 * @throws InvalidObjectException with a NullPointerException as its cause.
	 */
	public static <T> T notNull(T obj, String message) throws InvalidObjectException {
	    if (obj != null) return obj;
	    InvalidObjectException ex 
		    = new InvalidObjectException(message);
	    ex.initCause(new NullPointerException("Object was null"));
	    throw ex;
	}
	
	/**
	 * Convenience method to copy and type check all keys and values from
	 * the source map, into the destination map.
	 * 
	 * @param <T> Map or subtype.
	 * @param <K> key type.
	 * @param <V> value type.
	 * @param source any map containing unchecked keys and values.
	 * @param destination a map into which checked values and keys are to be copied.
	 * @param key Class of key to type check.
	 * @param val Class of value to type check.
	 * @return the populated destination map.
	 * @throws ClassCastException
	 * @throws NullPointerException if any parameter is null.
	 */
	public static <T extends Map<K,V>,K,V> T copyMap(T source, T destination, Class<K> key, Class<V> val)
	{
	    Map<K,V> typeCheckedView = Collections.checkedMap(destination, key, val);
	    typeCheckedView.putAll(source);
	    return destination;
	}
	
	/**
	 * Convenience method to copy and type check all elements from the
	 * source collection, into the destination collection.
	 * 
	 * @param <T> Collection or subtype.
	 * @param <E> Element type.
	 * @param source Collection containing unchecked elements.
	 * @param destination Empty Collection to populate with checked elements.
	 * @param type
	 * @return the populated destination collection.
	 * @throws ClassCastException
	 * @throws NullPointerException if any parameter is null.
	 */
	public static <T extends Collection<E>, E> T copyCol( T source, T destination, Class<E> type){  
	    Collection typeCheckedView = Collections.checkedCollection(destination, type);
	    typeCheckedView.addAll(source);
	    return destination;
	}
	
	protected GetArg() {
	    this(check());
	}
	
	GetArg(boolean check){
	    super();
	}

	/**
	 * Provides access to stream classes that belong to the Object under
	 * construction, ordered from superclass to child class.
	 *
	 * @return stream classes that belong to the object currently being
	 * de-serialized.
	 */
	public abstract Class[] serialClasses();

	/**
	 * If an AtomicSerial implementation annotates a static method that returns
	 * a Reader instance, with {@link ReadInput}, then the stream will provide
	 * the ReadObject access to the stream at a time that suits the stream.  
	 * This method provides a way for an object under construction to 
	 * retrieve information from the stream.  This is provided to retain
	 * compatibility with writeObject methods that write directly to the
	 * stream.
	 *
	 * @return ReadObject instance provided by static class method after it has
	 * read from the stream, or null.
	 */
	public abstract ReadObject getReader();
	
	
	/**
         * Get the value of the named Object field from the persistent field.
	 * Convenience method to avoid type casts, that also performs a type check.
	 * 
	 * Instances of java.util.Collection will be replaced in the stream
	 * by a safe limited functionality immutable Collection instance 
	 * that must be passed to a collection instance constructor.  It is
	 * advisable to pass a Collections empty collection instance for the
	 * val parameter, to prevent a NullPointerException, in this case.
         *
	 * @param <T> Type of object
         * @param  name the name of the field
         * @param  val the default value to use if <code>name</code> does not
         *         have a value
	 * @param type check to be performed, prior to returning.
         * @return the value of the named <code>Object</code> field
         * @throws IOException if there are I/O errors while reading from the
         *         underlying <code>InputStream</code>
         * @throws IllegalArgumentException if type of <code>name</code> is
         *         not serializable or if the field type is incorrect
	 * @throws InvalidObjectException containing a ClassCastException cause 
	 *	   if object to be returned is not an instance of type.
	 * @throws NullPointerException if type is null.
         */
        public abstract <T> T get(String name, T val, Class<T> type) 
		throws IOException;
    }
    
}


