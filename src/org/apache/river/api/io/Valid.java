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
import java.util.Map;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Utilities for validating invariants.
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
    
    public static <T> T isInstance(Class<T> type, Object o) throws InvalidObjectException{
	if (o == null) return null;
	if (type == null) throw new IllegalArgumentException("type cannot be null");
	if (type.isInstance(o)) return (T) o;
	throw new InvalidObjectException("Argument must be an instance of " + type);
    }
    
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
     * @param <T> Collection or subtype.
     * @param <E> Element type.
     * @param source Collection containing unchecked elements.
     * @param destination Empty Collection to populate with checked elements.
     * @param type
     * @return the populated destination collection.
     * @throws ClassCastException
     * @throws NullPointerException if any parameter is null.
     */
    public static <T extends Collection<E>, E> T copyCol(T source, T destination, Class<E> type) {
	Collection typeCheckedView = Collections.checkedCollection(destination, type);
	typeCheckedView.addAll(source);
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
     *
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

    public static byte[] copy(byte[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    public static boolean[] copy(boolean[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    public static char[] copy(char[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    public static short[] copy(short[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    public static int[] copy(int[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    public static long[] copy(long[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

    public static double[] copy(double[] arry) {
	if (arry == null) {
	    return null;
	}
	return arry.clone();
    }

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
    public static <T extends Map<K, V>, K, V> T copyMap(T source, T destination, Class<K> key, Class<V> val) {
	Map<K, V> typeCheckedView = Collections.checkedMap(destination, key, val);
	typeCheckedView.putAll(source);
	return destination;
    }
    
}
