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

package net.jini.core.constraint;

import java.io.InvalidObjectException;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;

/**
 * Constraint utility methods.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
class Constraint {

    /**
     * Non-instantiable.
     */
    private Constraint() {}

    /**
     * Returns an array containing the first len elements of the specified
     * array, with the same element type as the specified array.
     */
    static Object[] trim(Object[] elements, int len) {
	if (len == elements.length) {
	    return elements;
	}
	Object[] nelements =
	    (Object[]) Array.newInstance(
				 elements.getClass().getComponentType(), len);
	System.arraycopy(elements, 0, nelements, 0, len);
	return nelements;
    }

    /**
     * Returns the sum of the hash codes of all elements of the given array.
     */
    static int hash(Object[] elements) {
	int h = 0;
	for (int i = elements.length; --i >= 0; ) {
	    h += elements[i].hashCode();
	}
	return h;
    }

    /**
     * Returns true if the two arrays are the same length and contain equal
     * elements (but the order of the elements need not be the same in both
     * arrays). The arrays must not contain duplicates.
     */
    static boolean equal(Object[] arr1, Object[] arr2) {
	if (arr1 == arr2) {
	    return true;
	} else if (arr1.length != arr2.length) {
	    return false;
	}
	for (int i = arr1.length; --i >= 0; ) {
	    if (!contains(arr2, arr2.length, arr1[i])) {
		return false;
	    }
	}
	return true;
    }

    /**
     * Returns true if the non-null object is equal to any of the elements
     * of the array with index less than i.
     */
    static boolean contains(Object[] arr, int i, Object obj) {
	while (--i >= 0) {
	    if (obj.equals(arr[i])) {
		return true;
	    }
	}
	return false;
    }

    /**
     * Returns a sorted comma-separated list of the toString form of the
     * elements of the array. If the first element is a Class instance,
     * then all of the elements must be Class instances.
     */
    static String toString(Object[] a) {
	if (a.length == 0) {
	    return "{}";
	} else if (a.length == 1) {
	    String s;
	    if (a[0] instanceof Class) {
		s = ((Class) a[0]).getName();
	    } else {
		s = a[0].toString();
	    }
	    return "{" + s + "}";
	}
	String[] as = new String[a.length];
	int len = a.length * 2;
	if (a[0] instanceof Class) {
	    for (int i = a.length; --i >= 0; ) {
		String val = ((Class) a[i]).getName();
		as[i] = val;
		len += val.length();
	    }
	} else {
	    for (int i = a.length; --i >= 0; ) {
		String val = a[i].toString();
		as[i] = val;
		len += val.length();
	    }
	}
	Arrays.sort(as);
	StringBuffer buf = new StringBuffer(len);
	buf.append("{");
	for (int i = 0; i < as.length; i++) {
	    if (i > 0) {
		buf.append(", ");
	    }
	    buf.append(as[i]);
	}
	buf.append("}");
	return buf.toString();
    }

    /**
     * Verifies that all elements of the collection are instances of principal
     * classes, and that there is at least one element, and returns an array
     * of the elements, in arbitrary order, with duplicates removed.
     */
    static Principal[] reduce(Collection c) {
	try {
	    return reduce0((Principal[]) c.toArray(new Principal[c.size()]));
	} catch (ArrayStoreException e) {
	    throw new IllegalArgumentException(
				  "element of collection is not a Principal");
	}
    }

    /**
     * Verifies that there is at least one element, and returns a new array of
     * the elements, in arbitrary order, with duplicates removed.
     */
    static Principal[] reduce(Principal[] principals) {
	return reduce0((Principal[]) principals.clone());
    }

    /**
     * Verifies that there is at least one element, and returns an array of
     * the elements, in arbitrary order, with duplicates removed. The
     * argument may be modified. If no duplicates need to be removed, the
     * argument may be returned.
     */
    private static Principal[] reduce0(Principal[] principals) {
	if (principals.length == 0) {
	    throw new IllegalArgumentException(
				 "cannot create constraint with no elements");
	}
	int i = 0;
	for (int j = 0; j < principals.length; j++) {
	    Principal p = principals[j];
	    if (p == null) {
		throw new NullPointerException("elements cannot be null");
	    }
	    if (!contains(principals, i, p)) {
		principals[i++] = p;
	    }
	}
	return (Principal[]) trim(principals, i);
    }

    /**
     * Verifies that there is at least one element, and that there are no
     * duplicates;
     */
    static void verify(Principal[] principals) throws InvalidObjectException {
	if (principals == null || principals.length == 0) {
	    throw new InvalidObjectException(
				  "cannot create constraint with no elements");
	}
	for (int i = principals.length; --i >= 0; ) {
	    Principal p = principals[i];
	    if (p == null) {
		throw new InvalidObjectException("elements cannot be null");
	    }
	    if (contains(principals, i, p)) {
		throw new InvalidObjectException(
			  "cannot create constraint with duplicate elements");
	    }
	}
    }

    /**
     * Verifies that all elements of the collection are classes, and that
     * there is at least one element, and returns an array of the elements,
     * in arbitrary order, with redundant classes removed as follows.  For
     * any two classes c1 and c2, if c1.isAssignableFrom(c2) is true, then
     * c2 is removed if keepSupers is true, otherwise c1 is removed.
     */
    static Class[] reduce(Collection c, boolean keepSupers) {
	try {
	    return reduce0((Class[]) c.toArray(new Class[c.size()]),
			   keepSupers);
	} catch (ArrayStoreException e) {
	    throw new IllegalArgumentException(
				      "element of collection is not a Class");
	}
    }

    /**
     * Verifies that there is at least one element, and returns a new array
     * of the elements, in arbitrary order, with redundant classes removed as
     * follows.  For any two classes c1 and c2, if c1.isAssignableFrom(c2) is
     * true, then c2 is removed if keepSupers is true, otherwise c1 is removed.
     */
    static Class[] reduce(Class[] classes, boolean keepSupers) {
	return reduce0((Class[]) classes.clone(), keepSupers);
    }

    /**
     * Verifies that there is at least one element, and returns an array
     * of the elements, in arbitrary order, with redundant classes removed as
     * follows.  For any two classes c1 and c2, if c1.isAssignableFrom(c2) is
     * true, then c2 is removed if keepSupers is true, otherwise c1 is
     * removed. The array argument may be modified. If no classes need to
     * be removed, the array argument may be returned.
     *
     * Note #1: Here we're removing ck, and we close the gap by moving the
     * last already-processed element (common[i - 1]) down to replace it. We
     * don't need to arraycopy all of the elements down because order doesn't
     * matter. In the degenerate case (k == i - 1) we copy an element onto
     * itself, but that does no harm.
     */
    private static Class[] reduce0(Class[] classes, boolean keepSupers) {
	if (classes.length == 0) {
	    throw new IllegalArgumentException(
				 "cannot create constraint with no elements");
	}
	int i = 0;
    outer:
	for (int j = 0; j < classes.length; j++) {
	    Class cj = classes[j];
	    verify(cj);
	    for (int k = i; --k >= 0; ) {
		Class ck = classes[k];
		if (keepSupers ? ck.isAssignableFrom(cj) :
				 cj.isAssignableFrom(ck))
		{
		    continue outer;
		}
		if (keepSupers ? cj.isAssignableFrom(ck) :
				 ck.isAssignableFrom(cj))
		{
		    classes[k] = classes[--i]; // see note #1
		}
	    }
	    classes[i++] = cj;
	}
	return (Class[]) trim(classes, i);
    }

    /**
     * Verifies that the class is not a primitive or array class, and
     * either isn't final or is assignable to Principal.
     */
    static void verify(Class c) {
	if (c == null) {
	    throw new NullPointerException("elements cannot be null");
	}
	if (c.isArray() || c.isPrimitive() ||
	    (Modifier.isFinal(c.getModifiers()) &&
	     !Principal.class.isAssignableFrom(c)))
	{
	    throw new IllegalArgumentException("invalid class");
	}
    }

    /**
     * Verifies that there is at least one element, that they are all
     * valid classes, and that no class is assignable to any other class.
     */
    static void verify(Class[] classes) throws InvalidObjectException {
	if (classes == null || classes.length == 0) {
	    throw new InvalidObjectException(
				  "cannot create constraint with no elements");
	}
	for (int i = classes.length; --i >= 0; ) {
	    Class ci = classes[i];
	    if (ci == null) {
		throw new InvalidObjectException("elements cannot be null");
	    }
	    if (ci.isArray() || ci.isPrimitive() ||
		(Modifier.isFinal(ci.getModifiers()) &&
		 !Principal.class.isAssignableFrom(ci)))
	    {
		throw new InvalidObjectException("invalid class");
	    }
	    for (int j = i; --j >= 0; ) {
		Class cj = classes[j];
		if (ci.isAssignableFrom(cj) || cj.isAssignableFrom(ci)) {
		    throw new InvalidObjectException(
			  "cannot create constraint with redundant elements");
		}
	    }
	}
    }
}
