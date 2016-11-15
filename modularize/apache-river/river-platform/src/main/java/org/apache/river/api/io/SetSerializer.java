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
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import org.apache.river.api.io.AtomicSerial.GetArg;

/**
 * Immutable Set backed by an array.
 * 
 * The comparator in this set, does not determine order, the order of the
 * content array is determined by the comparator when serialized, because
 * the comparator or content may have been tampered with, in an untrusted
 * de-serialization stream, it is not used, but only provided for the clients
 * convenience.  The client must check the contents of the set as well as the
 * comparator type before use.
 * 
 * The equals method must not be called on this set, until content types have
 * been checked, to avoid gadget attacks.  This is the clients responsibility.
 * 
 * @param T - Client code is responsible for type checking the contents of this
 *            Set after de-serialization, Valid provides suitable methods for 
 *            doing so.
 * @author peter
 */
@AtomicSerial
class SetSerializer<T> extends AbstractSet<T> implements SortedSet<T>, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final T [] content;
    private final Comparator<? super T> comparator;
    
    /**
     * @ throws NullPointerException if set is null.
     */
    SetSerializer(Set<T> set){
        this(
            set.toArray((T[]) new Object [set.size()]),
            set instanceof SortedSet ?
                ((SortedSet<T>)set).comparator() 
                : null 
        );
    }
    
    /**
     * Atomic de-serialization constructor.
     * @param arg provided by atomic de-serialization framework.
     * @throws IOException if a problem occurs during de-serialization.
     * @throws InvalidObjectException containing a ClassCastException cause 
     *         if content is not an Object[] array type or comparator is not
     *         an instance of Comparator.
     */
    SetSerializer(GetArg arg) throws IOException {
        this(
            (T[]) arg.get("content", new Object[0], Object[].class),
            arg.get("comparator", null, Comparator.class)
        );
    }
    
    SetSerializer(T [] set){
	this(set, null);
    }
    
    /**
     * No exceptions thrown to avoid finalizer attack.
     * @param set
     * @param comp 
     */
    private SetSerializer(T [] set, Comparator<? super T> comp){
        content = set;
        comparator = comp;
    }

    @Override
    public Iterator<T> iterator() {
	return new IT<T>(content);
    }

    @Override
    public int size() {
	return content.length;
    }

    @Override
    public Comparator<? super T> comparator() {
	return comparator;
    }

    @Override
    public SortedSet<T> subSet(T fromElement, T toElement) {
	throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SortedSet<T> headSet(T toElement) {
	throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public SortedSet<T> tailSet(T fromElement) {
	throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public T first() {
	throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public T last() {
	throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    private static class IT<T> implements Iterator<T> {
	
	T [] set;
	int index;
	
	IT(T [] set){
	    this.set = set;
	    index = 0;
	}

	@Override
	public boolean hasNext() {
	    if (index < set.length) return true;
	    return false;
	}

	@Override
	public T next() {
	    if (index < set.length) return set[index++];
	    throw new NoSuchElementException();
	}

	@Override
	public void remove() {
	    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}
	
    }
    
}
