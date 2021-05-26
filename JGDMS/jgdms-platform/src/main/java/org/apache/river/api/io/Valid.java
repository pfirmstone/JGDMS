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

import java.io.InvalidObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Utilities for validating invariants.
 * <p>
 * Collections de-serialized by AtomicMarshalInputStream are always safe,
 * that is, they do not call hashCode or equals on elements.  These collections
 * are not intended to be used as Collections in object form and must be replaced
 * during construction.  Although Comparator's contained in SortedMap's and SortedSet's
 * are serialized, they are not used by any of the following methods.  It is
 * generally recommended, although not compulsory, to prefer constructing a Comparator
 * when constructing defensive copies, instead of using the de-serialized Comparator.
 * <p>
 * Before using a de-serialized Comparator, it should be type checked, to 
 * ensure it is suitable for comparison of types of elements contained in 
 * a SortedSet or SortedMap.
 * <p>
 * Since Java 8, HashMap key's and HashSet elements that implement Comparable 
 * are provided with improved protection against hash collision DOS.
 * <p>
 * It is recommended that when using Set's or Map's, that
 * Comparable keys or elements, or a Comparator, be used to avoid hash collisions.
 * Remember to type check all keys, values and elements first, convenience
 * methods have been provided here to ease type checks in Collections.
 * <p>
 * Note that an attacker may deliberately generate a hash collision to ensure
 * equals is called and try to install the collection in another object that 
 * later invokes it.  This is why type checking of all elements is so important.
 * <p>
 * When considering the security of a collection, remember that an attacker
 * may try to obtain a reference to the collection, as such, defensive copies 
 * of collections should not be allowed to leak through your API during 
 * de-serialization, as your object is accessible via shared references.
 * <p>
 * Users are cautioned against using Maps and Sets that use hashing functions
 * when keys or elements contained therein are not type specific.
 * 
 * @author peter
 */
public class Valid {
    private static Method clone;
    
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
    
    /**
     * Type checks an object is an instance of type and returns it cast as
     * the type if true, otherwise throws an InvalidObjectException.
     * 
     * @param <T> instance type to return.
     * @param type to check instance.
     * @param o Object instance to type check.
     * @return a type cast instance of o.
     * @throws InvalidObjectException 
     */
    public static <T> T isInstance(Class<T> type, Object o) throws InvalidObjectException{
	if (o == null) return null;
	if (type == null) throw new IllegalArgumentException("type cannot be null");
	if (type.isInstance(o)) return (T) o;
	throw new InvalidObjectException("Argument must be an instance of " + type);
    }
    
    /**
     * Checks class of an Object is equal to the Class type and returns it as
     * that type, if true and throws an InvalidObjectException if false.
     * 
     * @param <T> type cast object as.
     * @param type
     * @param o the Object.
     * @return the Object cast as type.
     * @throws InvalidObjectException 
     */
    public static <T> T hasClass(Class<T> type, Object o) throws InvalidObjectException{
	if (o == null) return null;
	if (type == null) throw new IllegalArgumentException("type cannot be null");
	if (type.equals(o.getClass())) return (T) o;
	throw new InvalidObjectException("Argument must be an instance of " + type);
    }

    /**
     * Convenience method to copy and type check all elements from the
     * source collection, into the destination collection.
     *
     * Instances of java.util.Collection will be replaced in the stream
     * by a safe limited functionality immutable Collection instance
     * that must be replaced during deserialization.
     * 
     * Do not use this to validate collections that use hashing and do
     * not protect adequately against hash collisions.  Since Java 8,
     * HashSet protects against hash collisions, only if elements are Comparable.
     *
     * @param <T> Collection or subtype.
     * @param <E> Element type.
     * @param source Collection containing unchecked elements.
     * @param destination Empty Collection to populate with checked elements.
     * @param type
     * @return the populated destination collection, or null if source is null.
     * @throws java.io.InvalidObjectException if invariant checks fail.
     * @throws NullPointerException if any parameter, other than source, is null.
     */
    public static <T extends Collection<E>, E> T copyCol(T source, T destination, Class<E> type) throws InvalidObjectException {
        if (source == null) return null;
        if (destination == null) throw new NullPointerException("destination cannot be null");
        if (type == null) throw new NullPointerException("type cannot be null");
        try {
            Collection typeCheckedView = Collections.checkedCollection(destination, type);
            typeCheckedView.addAll(source);
        } catch (ClassCastException ex){
            throwIOE(ex);
        } catch (NullPointerException ex){
            throwIOE(ex);
        } catch (IllegalArgumentException ex){
            throwIOE(ex);
        }
	return destination;
    }
    
   /**
     * Convenience method to copy and type check all elements from the
     * source collection, into the destination collection.
     * <p>
     * Instances of java.util.Set have been replaced in the stream
     * by a safe limited functionality immutable Set instance
     * that must be replaced during deserialization.
     * <p>
     * This method checks for hash collisions before populating the destination set.
     *
     * @param <T> Set or subtype.
     * @param <E> Element type.
     * @param source Collection containing unchecked elements.
     * @param destination Empty Collection to populate with checked elements.
     * @param type Element type.
     * @param allowableHashCollisions number of hash collisions allowed per bucket.
     * @return the populated destination collection, or null if source is null.
     * @throws java.io.InvalidObjectException if invariant checks fail.
     * @throws NullPointerException if any parameter (other than comp or source) is null.
     * @throws IllegalArgumentException if allowableHashCollisions is negative.
     */
    public static <T extends Set<E>, E> T copySet(T source, T destination, Class<E> type, int allowableHashCollisions) throws InvalidObjectException {
        if (source == null) return null;
        if (destination == null) throw new NullPointerException("destination cannot be null");
        if (type == null) throw new NullPointerException("type cannot be null");
        if (allowableHashCollisions < 0) throw 
                new IllegalArgumentException("allowableHashCollisions is negative");
        hashCollision(source, allowableHashCollisions);
        try {
            Set typeCheckedView = Collections.checkedSet(
                destination,
                type
            );
            typeCheckedView.addAll(source);
        } catch (ClassCastException ex){
            throwIOE(ex);
        } catch (NullPointerException ex){
            throwIOE(ex);
        } catch (IllegalArgumentException ex){
            throwIOE(ex);
        }
	return destination;
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
	if (obj != null) {
	    return obj;
	}
	InvalidObjectException ex = new InvalidObjectException(message);
	ex.initCause(new NullPointerException("Object was null"));
	throw ex;
    }
    
    /**
     * Checks all elements in an array for null values, if the arry parameter
     * is not null.
     * 
     * @param <T>
     * @param arry the array
     * @param message the message for the InvalidObjectException
     * @return the array or null if arry is null.
     * @throws InvalidObjectException if array contains null elements.
     */
    public static <T> T[] nullElement(T[] arry, String message) throws InvalidObjectException{
	if (arry == null) return null;
	for (int i = 0, l = arry.length; i < l; i++){
	    if (arry[i] == null) throw new InvalidObjectException(message);
	}
	return arry;
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
    public static <T> T[] deepCopy(T[] arry) throws CloneNotSupportedException {
	if (arry == null) {
	    return null;
	}
	T[] cpy = copy(arry);
	for (int i = 0, l = cpy.length; i < l; i++) {
	    cpy[i] = copy(cpy[i]);
	}
	return cpy;
    }

    /**
     * Convenience method to create a shallow copy of an array
     * if non null.
     * <p>
     * Since arrays are mutable, an attacker can retain a reference
     * to a de-serialized array, that allows an attacker to mutate that
     * array.
     *
     * @param <T> type
     * @param arry that will be cloned.
     * @return A clone of arry, or null if arry is null.
     */
    public static <T> T[] copy(T[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    /**
     * Convenience method to create a copy of a byte array
     * if non null.
     * <p>
     * Since arrays are mutable, an attacker can retain a reference
     * to a de-serialized array, that allows an attacker to mutate that
     * array.
     *
     * @param arry that will be cloned.
     * @return A clone of arry, or null if arry is null.
     */
    public static byte[] copy(byte[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    /**
     * Convenience method to create a copy of a boolean array
     * if non null.
     * <p>
     * Since arrays are mutable, an attacker can retain a reference
     * to a de-serialized array, that allows an attacker to mutate that
     * array.
     *
     * @param arry that will be cloned.
     * @return A clone of arry, or null if arry is null.
     */
    public static boolean[] copy(boolean[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    /**
     * Convenience method to create a copy of a char array
     * if non null.
     * <p>
     * Since arrays are mutable, an attacker can retain a reference
     * to a de-serialized array, that allows an attacker to mutate that
     * array.
     *
     * @param arry that will be cloned.
     * @return A clone of arry, or null if arry is null.
     */
    public static char[] copy(char[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    /**
     * Convenience method to create a copy of a short array
     * if non null.
     * <p>
     * Since arrays are mutable, an attacker can retain a reference
     * to a de-serialized array, that allows an attacker to mutate that
     * array.
     *
     * @param arry that will be cloned.
     * @return A clone of arry, or null if arry is null.
     */
    public static short[] copy(short[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    /**
     * Convenience method to create a copy of a int array
     * if non null.
     * <p>
     * Since arrays are mutable, an attacker can retain a reference
     * to a de-serialized array, that allows an attacker to mutate that
     * array.
     *
     * @param arry that will be cloned.
     * @return A clone of arry, or null if arry is null.
     */
    public static int[] copy(int[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    /**
     * Convenience method to create a copy of a long array
     * if non null.
     * <p>
     * Since arrays are mutable, an attacker can retain a reference
     * to a de-serialized array, that allows an attacker to mutate that
     * array.
     *
     * @param arry that will be cloned.
     * @return A clone of arry, or null if arry is null.
     */
    public static long[] copy(long[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    /**
     * Convenience method to create a copy of a double array
     * if non null.
     * <p>
     * Since arrays are mutable, an attacker can retain a reference
     * to a de-serialized array, that allows an attacker to mutate that
     * array.
     *
     * @param arry that will be cloned.
     * @return A clone of arry, or null if arry is null.
     */
    public static double[] copy(double[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    /**
     * Convenience method to create a copy of a float array
     * if non null.
     * <p>
     * Since arrays are mutable, an attacker can retain a reference
     * to a de-serialized array, that allows an attacker to mutate that
     * array.
     *
     * @param arry that will be cloned.
     * @return A clone of arry, or null if arry is null.
     */
    public static float[] copy(float[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    /**
     * Convenience method to copy Cloneable objects.
     *
     * @param <T>
     * @param obj
     * @return a clone of obj if non null, otherwise null;
     * @throws CloneNotSupportedException
     */
    public static <T> T copy(T obj) throws CloneNotSupportedException {
	if (obj == null) {
	    return null;
	}
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

    /**
     * Convenience method to copy and type check all keys and values from
     * the source map, into the destination map.
     * <p>
     * Note, this shouldn't be used to populate maps that don't have protection
     * against hash collisions.  Note that HashMap and ConcurrentHashMap,
     * since Java 8 defend against hash collisions, but only if keys are Comparable.
     * 
     * @param <T> Map or subtype.
     * @param <K> key type.
     * @param <V> value type.
     * @param source any map containing unchecked keys and values.
     * @param destination a map into which checked values and keys are to be copied.
     * @param key Class of key to type check.
     * @param val Class of value to type check.
     * @return the populated destination map, or null if source is null.
     * @throws java.io.InvalidObjectException if invariant checks fail.
     * @throws ClassCastException
     * @throws NullPointerException if any parameter other than source is null.
     */
    public static <T extends Map<K, V>, K, V> T copyMap
        (
            T source, 
            T destination, 
            Class<K> key, 
            Class<V> val
        ) 
            throws InvalidObjectException 
    {
        if (source == null) return null;
        if (destination == null) throw new NullPointerException("destination cannot be null");
        if (key == null) throw new NullPointerException("key cannot be null");
        if (val == null) throw new NullPointerException("val cannot be null");

        try {
            Map<K, V> typeCheckedView = Collections.checkedMap(destination, key, val);
            typeCheckedView.putAll(source);
        } catch (ClassCastException ex){
            throwIOE(ex);
        } catch (NullPointerException ex){
            throwIOE(ex);
        } catch (IllegalArgumentException ex){
            throwIOE(ex);
        }
        return destination;
    }
    
    /**
     * Convenience method to copy and type check all keys and values from
     * the source map, into the destination map.
     * 
     * This method checks for hash collisions before populating the destination
     * map.
     *
     * @param <T> Map or subtype.
     * @param <K> key type.
     * @param <V> value type.
     * @param source any map containing unchecked keys and values.
     * @param destination a map into which checked values and keys are to be copied.
     * @param key Class of key to type check.
     * @param val Class of value to type check.
     * @param allowableHashCollisions the number of hash collisions allowed per bucket.
     * @return the populated destination map, or null if source is null.
     * @throws java.io.InvalidObjectException if invariant checks fail.
     * @throws NullPointerException if any parameter (other than comp and source) is null.
     * @throws IllegalArgumentException if allowableHashCollisions is negative.
     */
    public static <T extends Map<K, V>, K, V> T copyMap
        (
            T source, 
            T destination, 
            Class<K> key,
            Class<V> val,
            int allowableHashCollisions
        ) throws InvalidObjectException 
    {
        if (source == null) return null;
        if (destination == null) throw new NullPointerException("destination cannot be null");
        if (key == null) throw new NullPointerException("key cannot be null");
        if (val == null) throw new NullPointerException("val cannot be null");
        if (allowableHashCollisions < 0) throw new IllegalArgumentException("allowableHashCollisions is negative");
        hashCollision(source.keySet(), allowableHashCollisions);
        try {
            Map<K, V> typeCheckedView = Collections.checkedMap(
                destination, 
                key, 
                val
            );
            typeCheckedView.putAll(source);
        } catch (ClassCastException ex){
            throwIOE(ex);
        } catch (NullPointerException ex){
            throwIOE(ex);
        } catch (IllegalArgumentException ex){
            throwIOE(ex);
        }
        return destination;
    }
        
    public static void throwIOE(Exception cause) throws InvalidObjectException{
        InvalidObjectException ioe = new InvalidObjectException("Input validation failed: ");
        ioe.initCause(cause);
        throw ioe;
    }
    
    private static void hashCollision(Collection col, int hashCollisionLimit) throws InvalidObjectException{
        Map<Integer,Integer> count = new HashMap<Integer,Integer>(col.size());
        Iterator it = col.iterator();
        while (it.hasNext()){
            Object t = it.next();
            Integer hash = t.hashCode();
            Integer i = count.get(hash);
            if (i == null) {
                count.put(hash,1);
            } else {
                i++;
                count.put(hash,i);
            }
        }
        Iterator<Entry<Integer,Integer>> countIt = count.entrySet().iterator();
        while (countIt.hasNext()){
            Entry<Integer,Integer> en = countIt.next();
            if( en.getValue() > hashCollisionLimit) throw new InvalidObjectException("Too many hash collisions");
        }
    }
}
