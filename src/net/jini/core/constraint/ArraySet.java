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

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * An immutable set backed by an array.  Any attempts to modify the set
 * result in UnsupportedOperationException being thrown.
 *
 * @author Sun Microsystems, Inc.
 * 
 */
final class ArraySet implements Set {
    /**
     * The array.
     */
    private final Object[] elements;

    /**
     * Creates an instance from an array. The array is not copied.
     */
    ArraySet(Object[] elements) {
	this.elements = elements;
    }

    /* inherit javadoc */
    public int size() {
	return elements.length;
    }

    /* inherit javadoc */
    public boolean isEmpty() {
	return elements.length == 0;
    }

    /* inherit javadoc */
    public boolean contains(Object o) {
	for (int i = elements.length; --i >= 0; ) {
	    if (elements[i].equals(o)) {
		return true;
	    }
	}
	return false;
    }

    /* inherit javadoc */
    public Iterator iterator() {
	return new Iter();
    }

    /**
     * Simple iterator.
     */
    private final class Iter implements Iterator {
	/**
	 * Index into the array.
	 */
	private int idx = 0;

	// compiler would generate crud because default constructor is private
	Iter() {
	}

	/* inherit javadoc */
	public boolean hasNext() {
	    return idx < elements.length;
	}

	/* inherit javadoc */
	public Object next() {
	    if (idx < elements.length) {
		return elements[idx++];
	    }
	    throw new NoSuchElementException();
	}

	/**
	 * Always throws UnsupportedOperationException.
	 */
	public void remove() {
	    throw new UnsupportedOperationException();
	}
    }

    /* inherit javadoc */
    public Object[] toArray() {
	Object[] a = new Object[elements.length];
	System.arraycopy(elements, 0, a, 0, elements.length);
	return a;
    }

    /* inherit javadoc */
    public Object[] toArray(Object a[]) {
	if (a.length < elements.length) {
	    a = (Object[]) Array.newInstance(a.getClass().getComponentType(),
					     elements.length);
	}
	System.arraycopy(elements, 0, a, 0, elements.length);
	if (a.length > elements.length) {
	    a[elements.length] = null;
	}
	return a;
    }

    /**
     * Always throws UnsupportedOperationException.
     */
    public boolean add(Object o) {
	throw new UnsupportedOperationException();
    }

    /**
     * Always throws UnsupportedOperationException.
     */
    public boolean remove(Object o) {
	throw new UnsupportedOperationException();
    }

    /* inherit javadoc */
    public boolean containsAll(Collection c) {
	Iterator iter = c.iterator();
	while (iter.hasNext()) {
	    if (!contains(iter.next())) {
		return false;
	    }
	}
	return true;
    }

    /**
     * Always throws UnsupportedOperationException.
     */
    public boolean addAll(Collection c) {
	throw new UnsupportedOperationException();
    }

    /**
     * Always throws UnsupportedOperationException.
     */
    public boolean retainAll(Collection c) {
	throw new UnsupportedOperationException();
    }

    /**
     * Always throws UnsupportedOperationException.
     */
    public boolean removeAll(Collection c) {
	throw new UnsupportedOperationException();
    }

    /**
     * Always throws UnsupportedOperationException.
     */
    public void clear() {
	throw new UnsupportedOperationException();
    }

    /* inherit javadoc */
    public boolean equals(Object o) {
	return (this == o ||
		(o instanceof Set &&
		 ((Collection) o).size() == elements.length &&
		 containsAll((Collection) o)));
    }

    /* inherit javadoc */
    public int hashCode() {
	return Constraint.hash(elements);
    }

    /* inherit javadoc */
    public String toString() {
	return Constraint.toString(elements);
    }
}
