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

package org.apache.river.lookup.util;

import java.io.IOException;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import org.apache.river.api.io.AtomicSerial;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * An implementation of the <code>java.util.Set</code> interface that has
 * a serialized form consistent in all virtual machines. <code>ConsistentSet</code>
 * instances are unmodifiable. All mutator methods, such as <code>add</code> and
 * <code>remove</code>, throw <code>UnsupportedOperationException</code>.
 * This class permits the <code>null</code> element.
 *
 * <p>
 * Although instances of this class are unmodifiable, they are not necessarily
 * immutable. If a client retrieves a mutable object contained in a
 * <code>ConsistentSet</code> and mutates that object, the client in effect
 * mutates the state of the <code>ConsistentSet</code>. In this case, the
 * serialized form of the <code>ConsistentSet</code> will also most likely
 * have been mutated. A <code>ConsistentSet</code> that contains only immutable
 * objects will maintain a consistent serialized form indefinitely. But a 
 * <code>ConsistentSet</code> that contains mutable objects will maintain a
 * consistent serialized form only so long as the mutable objects are not
 * mutated.
 *
 * @author Bill Venners
 */
@AtomicSerial
public class ConsistentSet<T> extends AbstractSet<T> implements Serializable {

    private static final long serialVersionUID = -533615203387369436L;

    /**
     * @serial An array of the <code>Object</code> elements contained in
     *     this <code>ConsistentSet</code>.
     */
    private final T[] elements;

    /**
     * Constructs a new, empty <code>ConsistentSet</code>. All instances
     * of <code>ConsistentSet</code> are unmodifiable.
     */
    @SuppressWarnings("unchecked")
    public ConsistentSet() {
        elements = (T[]) new Object[0];
    }

    /**
     * Constructs a new <code>ConsistentSet</code> containing the elements
     * in the passed collection. All instances of <code>ConsistentSet</code>
     * are unmodifiable.
     *
     * @param init the collection whose elements are to be placed into this set.
     * @throws NullPointerException if the passed <code>init</code> reference
     *     is <code>null</code>
     */
    @SuppressWarnings("unchecked")
    public ConsistentSet(Collection<T> init) {
	this(elements(init));
    }

    public ConsistentSet(GetArg arg) throws IOException {
	this(elements(Arrays.asList((T[])arg.get("elements", null))));
    }
    
    public static <T> T[] elements(Collection<T> init){
	
        if (init == null) {
            throw new NullPointerException();
        }

        // Put the collection in a HashSet to get rid of duplicates
        Set<T> tempSet = new HashSet<T>(init);

        return tempSet.toArray((T[]) new Object[init.size()]);
    }

    private ConsistentSet(T[] elements){
	this.elements = elements;
    }

    /**
     * Returns an <code>iterator</code> over the elements in this set. The elements
     * are returned in no particular order. Because all instances of
     * <code>ConsistentSet</code> are unmodifiable, the <code>remove</code> method 
     * of the returned <code>Iterator</code> throws
     * <code>UnsupportedOperationException</code>.
     *
     * @return an <code>Iterator</code> over the elements in this
     *     <code>ConsistentSet</code>.
     */
    @Override
    public Iterator<T> iterator() {

        return new Iterator<T>() {

            private int nextPos = 0;

            @Override
            public boolean hasNext() {
                return nextPos < elements.length;
            }

            @Override
            public T next() {
		if (!hasNext()) throw new NoSuchElementException();
                T next = elements[nextPos];
                ++nextPos;
                return next;
            }

            @Override
            public void remove() {
                throw new IllegalArgumentException();
            }
        };
    }

    /**
     * Returns the number of elements in this <code>ConsistentSet</code> (its cardinality).
     *
     * @return the number of elements in this <code>ConsistentSet</code> (its cardinality).
     */
    @Override
    public int size() {
        return elements.length;
    }
}

