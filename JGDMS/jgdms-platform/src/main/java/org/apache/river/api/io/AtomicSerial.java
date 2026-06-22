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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.jini.io.ObjectStreamContext;

/**
 * <h1>Atomic Serial - A public Serialization API</h1>
 * Java Serialization cannot be used over untrusted connections
 * for the following reasons:
 * <p>
 * <ol>
 * <li>The serial stream can be manipulated to allow the attacker to instantiate
 * any Serializable object available on the CLASSPATH, any object that
 * has a default constructor, such as ClassLoader.</li>
 * <li>A serial stream can be manipulated to cause denial of service and throw
 * an OOME.
 * </li>
 * <li>Filter mechanisms, don't address the underlying cause; the creation of
 * objects prior to their validation.</li>
 * </ol>
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
 * <h2>A requirement of implementing this interface is to implement a constructor
 * that accepts a single GetArg parameter.</h2>  
 * <p>
 * This constructor must be
 * public and the class given public visibility even if it has no other
 * public constructors.
 * <p>
 * <code>
 * public AtomicSerialImpl(GetArg args) throws InvalidObjectException{<br>
 * <br>
 * &emsp;	super(check(arg)); // If super also implements @AtomicSerial<br>
 * &emsp;	// Set fields here<br>
 * <br>
 * }<br>
 * </code>
 * <h2>
 * Before calling a superclass constructor, the class must
 * first call a static invariant check method, for example:</h2>
 * <p>
 * <code>
 * static GetArg check(GetArg args) throws InvalidObjectException, ClassNotFoundException;
 * </code>
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
 * While serializable fields of a class can be retrieved from the {@link ObjectStreamClass},
 * it presents a problem for future support, or for implementations that don't 
 * need the added burden of supporting Serializable.
 * <p>
 * The special static serializable field, serialPersistentFields, is an array
 * of ObjectStreamField components that defines serializable fields in the Java
 * Serialization Specification.
 * <p>
 * <h2>The serial form of an @AtomicSerial class, is defined by a public static 
 * serialPersistentFields method signature.</h2>
 * <p>
 * <code>
 * public static {@link SerialForm} [] serialForm()
 * </code>
 * <p>
 * <h3>Serial form is completely independent of any Object fields declared in the class.</h3>
 * <p>
 * For implementations also supporting Java Serialization, implements Serializable,
 * the static field serialPersistentFields should also be defined:
 * <p>
 * <code>
 * private static final {@link ObjectStreamField}[] serialPersistentFields = serialForm();
 * </code>
 * <p>
 * This ensures that both @AtomicSerial and Serialzable implementations avoid
 * duplication where possible.
 * <p>
 * The order of serializable fields defined in serialPersistentFields is preserved
 * as different serialization protocols may depend on it.
 * <p>
 * If the developer wants to maintain flexibility in serializable fields, to
 * be able to remove a field if no longer required, the developer must catch
 * IllegalArgumentException when calling
 * {@link GetArg#get(java.lang.String, java.lang.Object, java.lang.Class) }
 * <p>
 * Serializable fields, if used, must be explicitly written to and read from the stream,
 * during serialization and de-serialization.  If a class has no serializable
 * fields, then the special static method serialPersistentField should return
 * an empty array.
 * <p>
 * AtomicSerial ObjectInput and ObjectOutput implementations may communicate
 * using any serialization protocol.
 * <p>
 * The following method is an example of the signature that an @AtomicSerial class
 * implementation must use to serialize the internal state of an object
 * instance of itself.  Note the Object argument should be the type of the implementing
 * class.
 * <p>
 * Unlike Java Serialization, which uses private object instance methods,
 * a static method is used to allow each class in an Object's inheritance
 * hierarchy to write it's state to the stream, without needing the 
 * method to be private.
 * <p>
 *
 * <code>
 * public static void serialize(PutArg arg, T o) throws IOException
 * </code>
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
     * Used to annotate a class to indicate that it has no arguments, Objects
     * or data to write to the stream.  The class doesn't implement
     * the serialize or serialPersistentFields methods.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Stateless {}
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
				    {
					String sv = MarshalDelegates.strictBlockCtor(type, mods);
					if (sv != null) throw new InvalidClassException(type.getName(), sv);
					if (!MarshalDelegates.isStrict()) c.setAccessible(true); //strict skips; non-public class blocked above
					return c;
				    }
				case Modifier.PROTECTED:
				    throw new InvalidClassException( type.getCanonicalName(),
					"protected constructor cannot be called by de-serializer");
				case Modifier.PRIVATE:
				    throw new InvalidClassException( type.getCanonicalName(),
					"private constructor cannot be called by de-serializer");
				default: // Package private
				    {
					String sv = MarshalDelegates.strictBlockCtor(type, mods);
					if (sv != null) throw new InvalidClassException(type.getName(), sv);
					if (!MarshalDelegates.isStrict()) c.setAccessible(true); //strict skips; non-public ctor blocked above
					return c;
				    }
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
    public static abstract class GetArg implements ObjectStreamContext {

	/**
	 * Not intended for general construction, however may be extended
	 * by an ObjectInput implementation or for testing purposes.
	 * <p>
	 * As of the 4.0.0 Java-Serialization uncoupling this constructor is a
	 * no-op: the {@code SerializablePermission("enableSubclassImplementation")}
	 * guard has been dropped. The typed {@code get} accessors are {@code final}
	 * and memoize per {@code (callerClass, name)} (see {@link #lookup}), so an
	 * untrusted {@code GetArg} subclass cannot return one value to a class's
	 * {@code check(GetArg)} invariant check and a different value to its
	 * {@code (GetArg)} constructor; idempotency makes check-then-construct sound
	 * without a subclass-construction permission.
	 */
	protected GetArg() {
	}

	/**
	 * StackWalker used to resolve the {@code @AtomicSerial} caller class for
	 * field dispatch. {@code RETAIN_CLASS_REFERENCE} is required to read
	 * declaring classes; it is the same caller-introspection API the JDK itself
	 * uses for caller validation, and the only such mechanism that survives on a
	 * stock OpenJDK that has dropped the Authorization framework. Owning caller
	 * dispatch in the base (rather than in each impl) is what lets the typed
	 * {@code get} accessors be {@code final} and memoizing.
	 */
	private static final StackWalker WALKER =
		StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	/**
	 * Sentinel returned by {@link #lookup(Class, String)} when a field is absent
	 * or still holds its default value. Distinct from a real {@code null} field
	 * value. Visible to subclass implementations in other packages so a
	 * {@code lookup} hook can signal absence.
	 */
	protected static final Object ABSENT = new Object();

	/**
	 * Per-{@code (caller-class, field-name)} memoization of resolved field
	 * values. The outer key is the resolved {@code @AtomicSerial} caller class;
	 * the inner key is the field name; the value is the boxed field value
	 * (possibly {@code null}) or {@link #ABSENT}.
	 * <p>
	 * This is what makes the typed {@code get} accessors IDEMPOTENT: the first
	 * read of a {@code (caller, name)} pair invokes {@link #lookup} exactly once
	 * and caches the result, so a hostile or replayed {@code GetArg} cannot
	 * return one value to a class's {@code check(GetArg)} invariant check and a
	 * different value to its {@code (GetArg)} constructor (TOCTOU). The
	 * {@code get} overloads are {@code final}, so a subclass can only influence
	 * a field value through {@code lookup}, which the base calls at most once.
	 */
	private final Map<Class<?>, Map<String, Object>> cache = new HashMap<Class<?>, Map<String, Object>>();

	/** Cached set-form view of {@link #serialClasses()} for caller filtering. */
	private volatile Set<Class<?>> serialClassSet;

	private Set<Class<?>> serialClassSet() {
	    Set<Class<?>> s = serialClassSet;
	    if (s == null) {
		Class[] classes = serialClasses();
		s = new HashSet<Class<?>>(classes.length * 2);
		for (Class<?> c : classes) s.add(c);
		serialClassSet = s;
	    }
	    return s;
	}

	/**
	 * Resolves the {@code @AtomicSerial} class on whose behalf a {@code get} or
	 * {@code defaulted} call is being made, by walking the call stack and
	 * returning the first frame whose declaring class is one of
	 * {@link #serialClasses()}. If exactly one class is registered and no frame
	 * matches (e.g. synthetic bridge or lambda frames sit between the caller and
	 * here), that single class is returned as a safe fallback.
	 *
	 * @return the resolved caller class
	 * @throws InvalidObjectException if no registered class is on the stack and
	 *         more than one class is registered (ambiguous hierarchy)
	 */
	protected Class<?> callerClass() throws InvalidObjectException {
	    final Set<Class<?>> serial = serialClassSet();
	    Class<?> found = WALKER.walk(frames ->
		frames.map(StackWalker.StackFrame::getDeclaringClass)
		      .filter(serial::contains)
		      .findFirst()
		      .orElse(null));
	    if (found != null) return found;
	    if (serial.size() == 1) return serial.iterator().next();
	    throw new InvalidObjectException(
		"GetArg: cannot determine caller @AtomicSerial class from stack; "
		+ "registered classes: " + serial);
	}

	/**
	 * Resolves and memoizes the value of field {@code name} for the current
	 * caller class. {@link #lookup} is invoked at most once per
	 * {@code (caller, name)}; the boxed result (or {@link #ABSENT}) is cached and
	 * returned on every subsequent read. This is the idempotency guarantee that
	 * makes check-then-construct sound even against an untrusted {@code GetArg}.
	 */
	private Object cachedLookup(String name)
		throws IOException, ClassNotFoundException
	{
	    Class<?> caller = callerClass();
	    synchronized (cache) {
		Map<String, Object> byName = cache.get(caller);
		if (byName == null) {
		    byName = new HashMap<String, Object>();
		    cache.put(caller, byName);
		}
		if (byName.containsKey(name)) {
		    return byName.get(name);
		}
		Object value = lookup(caller, name);
		byName.put(name, value);
		return value;
	    }
	}

	/**
	 * Fetches the boxed value of field {@code name} for the already-resolved
	 * {@code callerClass}, or {@link #ABSENT} when the field is absent/defaulted.
	 * Implemented by subclasses; called at most once per {@code (caller, name)}
	 * by {@link #cachedLookup(String)} -- the base owns memoization, so a
	 * subclass MUST NOT cache and MUST NOT depend on call count. Any lazily
	 * decoded nested value is decoded here and then memoized by the base
	 * (decode-once), so the cumulative nesting/DoS guard is honoured exactly
	 * once per field.
	 *
	 * @param callerClass the resolved {@code @AtomicSerial} caller class
	 * @param name        the field name
	 * @return the boxed field value (possibly {@code null}), or {@link #ABSENT}
	 */
	protected abstract Object lookup(Class<?> callerClass, String name)
		throws IOException, ClassNotFoundException;

	/**
	 * Reports whether field {@code name} is absent or still holds its default
	 * value for the already-resolved {@code callerClass}, WITHOUT decoding any
	 * value, so {@link #defaulted(String)} stays side-effect free. Implemented
	 * by subclasses.
	 *
	 * @param callerClass the resolved {@code @AtomicSerial} caller class
	 * @param name        the field name
	 * @return {@code true} if the field is absent or defaulted
	 */
	protected abstract boolean isDefaulted(Class<?> callerClass, String name)
		throws IOException;

	/**
	 * Returns true if the field named {@code name} has not been assigned
	 * a value and still holds a default value for its type, false otherwise.
	 *
	 * @param name the name of the field to test
	 * @return true if the field holds its default value, false otherwise
	 * @throws IOException if an I/O error occurs
	 * @throws IllegalArgumentException if the corresponding field cannot be found
	 */
	public final boolean defaulted(String name) throws IOException {
	    return isDefaulted(callerClass(), name);
	}

	/**
	 * Get the value of the named boolean field from the persistent field.
	 * @param name  the name of the field
	 * @param val   the default value to use if {@code name} does not have a value
	 * @return the value of the named {@code boolean} field
	 * @throws IOException if there are I/O errors while reading from the underlying stream
	 */
	public final boolean get(String name, boolean val) throws IOException {
	    try {
		Object v = cachedLookup(name);
		return v == ABSENT ? val : ((Boolean) v).booleanValue();
	    } catch (ClassNotFoundException e) {
		throw new IOException("Unexpected class resolution failure for field: " + name, e);
	    }
	}

	/**
	 * Get the value of the named byte field from the persistent field.
	 * @param name  the name of the field
	 * @param val   the default value
	 * @return the value of the named {@code byte} field
	 * @throws IOException if there are I/O errors
	 */
	public final byte get(String name, byte val) throws IOException {
	    try {
		Object v = cachedLookup(name);
		return v == ABSENT ? val : ((Byte) v).byteValue();
	    } catch (ClassNotFoundException e) {
		throw new IOException("Unexpected class resolution failure for field: " + name, e);
	    }
	}

	/**
	 * Get the value of the named char field from the persistent field.
	 * @param name  the name of the field
	 * @param val   the default value
	 * @return the value of the named {@code char} field
	 * @throws IOException if there are I/O errors
	 */
	public final char get(String name, char val) throws IOException {
	    try {
		Object v = cachedLookup(name);
		return v == ABSENT ? val : ((Character) v).charValue();
	    } catch (ClassNotFoundException e) {
		throw new IOException("Unexpected class resolution failure for field: " + name, e);
	    }
	}

	/**
	 * Get the value of the named short field from the persistent field.
	 * @param name  the name of the field
	 * @param val   the default value
	 * @return the value of the named {@code short} field
	 * @throws IOException if there are I/O errors
	 */
	public final short get(String name, short val) throws IOException {
	    try {
		Object v = cachedLookup(name);
		return v == ABSENT ? val : ((Short) v).shortValue();
	    } catch (ClassNotFoundException e) {
		throw new IOException("Unexpected class resolution failure for field: " + name, e);
	    }
	}

	/**
	 * Get the value of the named int field from the persistent field.
	 * @param name  the name of the field
	 * @param val   the default value
	 * @return the value of the named {@code int} field
	 * @throws IOException if there are I/O errors
	 */
	public final int get(String name, int val) throws IOException {
	    try {
		Object v = cachedLookup(name);
		return v == ABSENT ? val : ((Integer) v).intValue();
	    } catch (ClassNotFoundException e) {
		throw new IOException("Unexpected class resolution failure for field: " + name, e);
	    }
	}

	/**
	 * Get the value of the named long field from the persistent field.
	 * @param name  the name of the field
	 * @param val   the default value
	 * @return the value of the named {@code long} field
	 * @throws IOException if there are I/O errors
	 */
	public final long get(String name, long val) throws IOException {
	    try {
		Object v = cachedLookup(name);
		return v == ABSENT ? val : ((Long) v).longValue();
	    } catch (ClassNotFoundException e) {
		throw new IOException("Unexpected class resolution failure for field: " + name, e);
	    }
	}

	/**
	 * Get the value of the named float field from the persistent field.
	 * @param name  the name of the field
	 * @param val   the default value
	 * @return the value of the named {@code float} field
	 * @throws IOException if there are I/O errors
	 */
	public final float get(String name, float val) throws IOException {
	    try {
		Object v = cachedLookup(name);
		return v == ABSENT ? val : ((Float) v).floatValue();
	    } catch (ClassNotFoundException e) {
		throw new IOException("Unexpected class resolution failure for field: " + name, e);
	    }
	}

	/**
	 * Get the value of the named double field from the persistent field.
	 * @param name  the name of the field
	 * @param val   the default value
	 * @return the value of the named {@code double} field
	 * @throws IOException if there are I/O errors
	 */
	public final double get(String name, double val) throws IOException {
	    try {
		Object v = cachedLookup(name);
		return v == ABSENT ? val : ((Double) v).doubleValue();
	    } catch (ClassNotFoundException e) {
		throw new IOException("Unexpected class resolution failure for field: " + name, e);
	    }
	}

	/**
	 * Get the value of the named Object field from the persistent field.
	 * @param name  the name of the field
	 * @param val   the default value
	 * @return the value of the named {@code Object} field
	 * @throws IOException if there are I/O errors
	 * @throws ClassNotFoundException if class of a serialized object cannot be found
	 */
	public final Object get(String name, Object val) throws IOException, ClassNotFoundException {
	    Object v = cachedLookup(name);
	    return v == ABSENT ? val : v;
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
	 * retrieve information read directly from the stream by a {@link ReadInput}
	 * annotated reader method, prior to Object instantiation.
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
        public final <T> T get(String name, T val, Class<T> type)
		throws IOException, ClassNotFoundException
	{
	    if (type == null) throw new NullPointerException("type");
	    Object v = cachedLookup(name);
	    if (v == ABSENT || v == null) return val;
	    if (type.isInstance(v)) {
		@SuppressWarnings("unchecked")
		T result = (T) v;
		return result;
	    }
	    InvalidObjectException e = new InvalidObjectException(
		    "Input validation failed for field: " + name);
	    e.initCause(new ClassCastException(
		    "Field '" + name + "' is a " + v.getClass().getName()
		    + ", not assignable to " + type.getName()));
	    throw e;
	}

	/**
	 * Simple invariant check helper for subclasses with no intra-hierarchy
	 * invariants: forces each field to be read (so primitive decode errors
	 * surface) and checks object fields for nullability and type. Each field is
	 * read through the {@code final} memoizing {@code get} accessors, so the
	 * values validated here are guaranteed identical to the values the
	 * constructor subsequently reads.
	 *
	 * @param fields  field names
	 * @param types   the type of each field, parallel to {@code fields}
	 * @param nonNull {@code true} entries mark fields that must not be null
	 * @return this {@code GetArg}
	 * @throws IOException if invariants are not satisfied
	 * @throws NullPointerException if any argument is null
	 * @throws IllegalArgumentException if array lengths differ
	 */
	public final GetArg validateInvariants(String[] fields,
					       Class[] types,
					       boolean[] nonNull) throws IOException
	{
	    if (fields == null || types == null || nonNull == null)
		throw new NullPointerException("null arguments not allowed");
	    if (fields.length != types.length || fields.length != nonNull.length)
		throw new IllegalArgumentException("array arguments must have equal lengths");
	    for (int i = 0, l = fields.length; i < l; i++) {
		Class<?> type = types[i];
		String name = fields[i];
		if (type.isPrimitive()) {
		    // Force a read to confirm the field is present / decodable.
		    if (type == boolean.class) get(name, false);
		    else if (type == byte.class) get(name, (byte) 0);
		    else if (type == char.class) get(name, (char) 0);
		    else if (type == short.class) get(name, (short) 0);
		    else if (type == int.class) get(name, 0);
		    else if (type == long.class) get(name, 0L);
		    else if (type == float.class) get(name, 0.0F);
		    else if (type == double.class) get(name, 0.0D);
		} else {
		    Object o;
		    try {
			o = get(name, (Object) null);
		    } catch (ClassNotFoundException e) {
			InvalidObjectException ex = new InvalidObjectException(
				"Failed to resolve class for field: " + name);
			ex.initCause(e);
			throw ex;
		    }
		    if (nonNull[i] && o == null) {
			throw new InvalidObjectException(name + " cannot be null");
		    } else if (o != null && !type.isInstance(o)) {
			throw new InvalidObjectException(
				name + " must be an instance of " + type);
		    }
		}
	    }
	    return this;
	}

	}
    
    /**
     * Parameter argument received by an @AtomicSerial object in order to 
     * serialize its internal object state.
     */
    public static abstract class PutArg implements ObjectStreamContext {

	/**
	 * To be implemented by a Serialization framework.
	 * <p>
	 * As of the 4.0.0 Java-Serialization uncoupling this constructor is a
	 * no-op: the {@code SerializablePermission("enableSubclassImplementation")}
	 * guard has been dropped (the same guard was removed from {@link GetArg} --
	 * see that constructor for the idempotency rationale).
	 */
	protected PutArg() {
	}

        /**
         * Find and set the boolean value of a given field named {@code name}.
         * @param name  the name of the field to set
         * @param value new value for the field
         */
        public abstract void put(String name, boolean value);

        /**
         * Find and set the byte value of a given field named {@code name}.
         * @param name  the name of the field to set
         * @param value new value for the field
         */
        public abstract void put(String name, byte value);

        /**
         * Find and set the char value of a given field named {@code name}.
         * @param name  the name of the field to set
         * @param value new value for the field
         */
        public abstract void put(String name, char value);

        /**
         * Find and set the double value of a given field named {@code name}.
         * @param name  the name of the field to set
         * @param value new value for the field
         */
        public abstract void put(String name, double value);

        /**
         * Find and set the float value of a given field named {@code name}.
         * @param name  the name of the field to set
         * @param value new value for the field
         */
        public abstract void put(String name, float value);

        /**
         * Find and set the int value of a given field named {@code name}.
         * @param name  the name of the field to set
         * @param value new value for the field
         */
        public abstract void put(String name, int value);

        /**
         * Find and set the long value of a given field named {@code name}.
         * @param name  the name of the field to set
         * @param value new value for the field
         */
        public abstract void put(String name, long value);

        /**
         * Find and set the short value of a given field named {@code name}.
         * @param name  the name of the field to set
         * @param value new value for the field
         */
        public abstract void put(String name, short value);

        /**
         * Find and set the Object value of a given field named {@code name}.
         * @param name  the name of the field to set
         * @param value new value for the field
         */
        public abstract void put(String name, Object value);
        
        /**
         * Write buffered fields of the calling Object to the stream.
         * 
         * @throws IOException 
         */
        public abstract void writeArgs() throws IOException;
    }
    
    /**
     * A serial argument used by an {@link AtomicSerial} implementation to
     * define each serial argument populated into {@link PutArg} and {@link GetArg}
     * by name and type.
     *
     * <p>Ordering: primitives before non-primitives; within each group,
     * alphabetical by name. This reproduces the {@code ObjectStreamField}
     * ordering so that {@code Arrays.sort(SerialForm[])} yields the same
     * field order as the legacy JOSS path.
     */
    public static final class SerialForm implements Comparable<SerialForm> {

        private final String name;
        private final Class<?> type;
        private final boolean unshared;

        public SerialForm(String name, Class<?> type, boolean unshared) {
            if (name == null) throw new NullPointerException("name");
            if (type == null) throw new NullPointerException("type");
            this.name = name;
            this.type = type;
            this.unshared = unshared;
        }

        public SerialForm(String name, Class<?> type) {
            this(name, type, false);
        }

        /** Returns the name of the field. */
        public String getName() {
            return name;
        }

        /** Returns the type of the field. */
        public Class<?> getType() {
            return type;
        }

        /** Returns true if the field is unshared. */
        public boolean isUnshared() {
            return unshared;
        }

        /**
         * Ordering mirrors {@code ObjectStreamField}: primitive fields before
         * non-primitive fields, then alphabetically by name within each group.
         */
        @Override
        public int compareTo(SerialForm other) {
            boolean thisPrim = this.type.isPrimitive();
            boolean otherPrim = other.type.isPrimitive();
            if (thisPrim != otherPrim) {
                return thisPrim ? -1 : 1;
            }
            return this.name.compareTo(other.name);
        }

        @Override
        public String toString() {
            return "SerialForm(" + name + "," + type.getName() + (unshared ? ",unshared" : "") + ")";
        }
    }

}


