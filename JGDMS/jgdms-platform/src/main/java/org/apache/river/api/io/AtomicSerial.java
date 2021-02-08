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
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.ObjectStreamClass;
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
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import net.jini.io.ObjectStreamContext;

/**
 * Java Serialization cannot be used over untrusted connections
 * for the following reasons:
 * <p>
 * The serial stream can be manipulated to allow the attacker to instantiate
 * any Serializable object available on the CLASSPATH or any object that
 * has a default constructor, such as ClassLoader.
 * <p>
 * AtomicSerial is a public API designed to for any Serialization framework to utilise
 * for serialization and de-serialization of internal object state using any protocol in
 * a manner that allows Object classes to defensively validate invariants
 * atomically during de-serialization, prior to Object instance creation.
 * <p>
 * Developers implementing AtomicSerial will be able to utilise any serialization
 * framework or serialization framework decorator written using the AtomicSerial API.
 * <p>
 * Where practical, existing interfaces and classes used for Java Serialization
 * have been utilised.
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
 * A requirement of implementing this interface is to implement a public static method
 * signature that accepts 
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
 * AtomicSerial allows backward compatibility with Java Serializable classes, but
 * defines a public API for accessing Object state for Serialization.  AtomicSerial
 * is designed to be Serialization protocol agnostic and instead provides an
 * API for AtomicSerial classes to implement that allows Serialization frameworks
 * to access to Object state for serialization and construction during deserialization,
 * without breaking Object encapsulation.
 * <p> 
 * AtomicSerial provides backward compatibility with 
 * Serializable classes that implement writeObject and write other Objects
 * or primitives to the stream when {@link ReadObject} and {@link ReadInput}
 * are implemented by the class.
 * <p>
 * An {@link ObjectStreamField} represents a serializable field of an AtomicSerial class.
 * The serializable fields of a class can be retrieved from the {@link ObjectStreamClass}.
 * <p>
 * The special static serializable field, serialPersistentFields, is an array
 * of ObjectStreamField components that defines serializable fields.   
 * The serial form of an @AtomicSerial class, defined by serialPersistentFields
 * is completely independent of any Object fields declared in the class.
 * Serializable fields should be thought of as parameters passed to a constructor.
 * <p>
 * The order of serializable fields defined in serialPersistentFields is not important.
 * If the developer wants to maintain flexibility in serializable fields, to
 * be able to remove a field if no longer required, the developer must catch
 * IllegalArgumentException when calling
 * {@link GetArg#get(java.lang.String, java.lang.Object, java.lang.Class) }
 * <p>
 * Serializable fields, if used, must be explicitly written to and read from the stream,
 * during serialization and de-serialization.  If a class has no serializable
 * fields, then the special static field serialPersistentField should refer
 * to an empty array.
 * <p>
 * AtomicSerial ObjectInput and ObjectOutput implementations may communicate
 * using any serialization protocol.
 * 
 * @author Peter Firmstone.
 * @see ReadObject
 * @see ReadInput
 * @see GetArg
 * @see PutArg
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AtomicSerial {
     
    
    /**
     * ReadObject that can be used to read in data and Objects written
     * to the stream by writeObject() methods.
     * 
     * @see  ReadInput
     */
    public interface ReadObject {

        /**
         * This method by default calls {@link ReadObject#read(java.io.ObjectInput)} ,
         * it should be overridden, when the client needs to ensure the type
         * correctness of objects read from the stream.  Serialization framework
         * decorator implementations should only call this method.
         * 
         * @param input
         * @throws IOException
         * @throws ClassNotFoundException
         */
        default void read(AtomicObjectInput input) throws IOException, ClassNotFoundException{
            read((ObjectInput) input);
        }
        
        /**
         * This method must be implemented by clients wishing to read in Objects
         * or primitive data directly from the stream.
         * 
         * Serialization framework implementations should not call this method
         * directly.
         * 
         * @param input
         * @throws IOException
         * @throws ClassNotFoundException
         */
        void read(ObjectInput input) throws IOException, ClassNotFoundException ;
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
	 * @throws java.lang.ClassNotFoundException 
	 * @throws java.io.InvalidObjectException if invariant check fails
	 * @throws NullPointerException if arg or type is null.
	 */
	public static <T> T instantiate(final Class<T> type, final GetArg arg)
		throws IOException, ClassNotFoundException {
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
		if (e instanceof IOException) throw (IOException) e;
		if (e instanceof ClassNotFoundException) throw (ClassNotFoundException) e;
		if (e instanceof RuntimeException) throw (RuntimeException) e;
		InvalidObjectException ioe = new InvalidObjectException(
		    "Construction failed: " + type);
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
	
	/**
	 * Convenience method to test retrieval of a new ReadObject instance from
	 * a class static method annotated with @ReadInput
	 * 
	 * @see ReadInput
	 * @param streamClass
	 * @return
	 * @throws IOException 
	 */
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
	
	/**
         * Not intended for general construction, however may be extended
         * by an ObjectInput implementation or for testing purposes.
         * 
         * @throws SecurityException if caller doesn't have permission java.io.SerializablePermission "enableSubclassImplementation";
         */
	protected GetArg() {
	    this(Check.check());
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
	 * the ReadObject access to the stream at a time that suits the stream, 
         * prior to Object instantiation.
	 * This method provides a way for an object under construction to 
	 * retrieve information from the stream.  This is provided to retain
	 * compatibility with {@link PutArg#output() } when ObjectOutput is used 
         * to write Objects directly to the stream.
	 *
	 * @return ReadObject instance provided by static class method after it has
	 * read from the stream, or null.
	 */
	public abstract ReadObject getReader();
	
	
	/**
         * Get the value of the named Object field from the persistent field.
	 * Convenience method to avoid type casts, that also performs a type check.
	 * <p>
	 * Instances of java.util.Collection will be replaced in the stream
	 * by a safe limited functionality immutable Collection instance 
	 * that must be passed to a collection instance constructor.  It is
	 * advisable to pass a Collections empty collection instance for the
	 * val parameter, to prevent a NullPointerException, in this case.
         *
	 * @param <T> Type of object, note if T is an instance of Class&lt;? extends SomeClass&gt;
         * the you must validate it, as this method can't.
         * @param  name the name of the field
         * @param  val the default value to use if <code>name</code> does not
         *         have a value
	 * @param type check to be performed, prior to returning.
         * @return the value of the named <code>Object</code> field
         * @throws IOException if there are I/O errors while reading from the
         *         underlying <code>InputStream</code>
	 * @throws java.lang.ClassNotFoundException if class is not resolvable
	 *	   from the default loader.
         * @throws IllegalArgumentException if type of <code>name</code> is
         *         not serializable or if the field type is incorrect
	 * @throws InvalidObjectException containing a ClassCastException cause 
	 *	   if object to be returned is not an instance of type.
	 * @throws NullPointerException if type is null.
         */
        public abstract <T> T get(String name, T val, Class<T> type) 
		throws IOException, ClassNotFoundException;
	
	public abstract GetArg validateInvariants(  String[] fields, 
						    Class[] types,
						    boolean[] nonNull) 
							throws IOException;	  
	
	}
    
    /**
     * Parameter argument received by an @AtomicSerial object in order to 
     * serialize its internal object state.
     */
    public static abstract class PutArg extends ObjectOutputStream.PutField
                                        implements ObjectStreamContext {
        
        /**
         * This method is an example of the signature that an @AtomicSerial class
         * implementation must use to serialize the internal state of an object
         * instance of itself.
         * <p>
         * Unlike Java Serialization, which uses private object instance methods,
         * a static method is used to allow each class in an Object's inheritance
         * hierarchy to write it's state to the stream, without needing the 
         * method to be private.
         * 
         * @param arg PutArg argument used to store object fields.
         * @param o Object instance of the class to be serialized, it must be the 
         *          same type as the class implementing this method.
         * @throws IOException 
         * @throws IllegalArgumentException if Object is not an instance of
         *         the class implementing this method.
         */
        public static void serialize(PutArg arg, Object o) throws IOException {
            
        }
	
	/**
         * To be implemented by a Serialization framework.
         * 
         * @throws SecurityException if caller doesn't have permission java.io.SerializablePermission "enableSubclassImplementation";
         */
	protected PutArg() {
	    this(Check.check());
	}
        
        PutArg(boolean check){
	    super();
	}
        
        /**
         * Write buffered fields of the calling Object to the stream.
         * 
         * @throws IOException 
         */
        public abstract void writeFields() throws IOException;
        
        /**
         * Provides access to underlying ObjectOutput for writing fields out in
         * order.
         * 
         * @return 
         */
        public abstract ObjectOutput output();
    }
    
}


